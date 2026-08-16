package dev.fos.service;

import dev.fos.model.DrillLog;
import dev.fos.model.ProgressStatus;
import dev.fos.model.QuizAttempt;
import dev.fos.model.SrsReview;
import dev.fos.model.UserProgress;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.QuizAttemptRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.repo.UserProgressRepository;
import dev.fos.web.dto.MetricsDtos;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mede os quatro critérios de sucesso do MVP (docs/05-mvp-web-plano.md).
 *
 * <p>Existe porque a alternativa é avaliar 30 dias de uso por memória — e o critério de falha
 * declarado no plano ("o app está sendo aberto só para não perder o streak") é justamente o tipo de
 * coisa que a impressão pessoal não detecta.
 */
@Service
public class MvpMetricsService {

    /** Janela do plano de validação. */
    public static final int DEFAULT_WINDOW_DAYS = 30;

    private static final int MAX_WINDOW_DAYS = 365;

    // Metas de docs/05-mvp-web-plano.md.
    static final int TARGET_DAYS_WITH_DRILL = 12;
    static final int TARGET_SRS_ADHERENCE_PERCENT = 60;
    static final int TARGET_NODES_COMPLETED = 15;
    /** "Qualquer ocorrência" — uma única repetição espontânea já é o sinal que interessa. */
    static final int TARGET_QUIZ_RETAKES = 1;

    private final DrillLogRepository drillLogRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final SrsReviewRepository srsRepository;
    private final UserProgressRepository progressRepository;
    private final Clock clock;

    public MvpMetricsService(
            DrillLogRepository drillLogRepository,
            QuizAttemptRepository quizAttemptRepository,
            SrsReviewRepository srsRepository,
            UserProgressRepository progressRepository,
            Clock clock) {
        this.drillLogRepository = drillLogRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.srsRepository = srsRepository;
        this.progressRepository = progressRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MetricsDtos.MvpMetrics metrics(Long userId, LocalDate today, int windowDays) {
        if (windowDays < 1 || windowDays > MAX_WINDOW_DAYS) {
            throw new IllegalArgumentException(
                    "Janela de medição deve estar entre 1 e " + MAX_WINDOW_DAYS + " dias: " + windowDays);
        }

        LocalDate windowStart = today.minusDays(windowDays - 1L);
        List<DrillLog> drills = drillLogRepository.findByUserIdAndDrilledOnGreaterThanEqual(userId, windowStart);

        return new MetricsDtos.MvpMetrics(
                windowStart,
                today,
                windowDays,
                windowDays == DEFAULT_WINDOW_DAYS,
                MetricsDtos.Counted.of(daysWithDrill(drills, today), TARGET_DAYS_WITH_DRILL),
                srsAdherence(userId, drills, windowStart, today),
                MetricsDtos.Counted.of(nodesCompleted(userId, windowStart, today), TARGET_NODES_COMPLETED),
                MetricsDtos.Counted.of(quizRetakes(userId, windowStart, today), TARGET_QUIZ_RETAKES));
    }

    /** Dias distintos com registro — o hábito sobreviveu à rotina? */
    private long daysWithDrill(List<DrillLog> drills, LocalDate today) {
        return drills.stream()
                .map(DrillLog::getDrilledOn)
                .filter(date -> !date.isAfter(today))
                .distinct()
                .count();
    }

    /**
     * Aderência à agenda do SRS — a sugestão de "o que drillar" é usada ou ignorada?
     *
     * <p>Atendida é a revisão que estava vencida e recebeu drill; o mesmo nó drilado várias vezes no
     * mesmo dia conta uma vez, senão insistir em um nó inflaria a aderência.
     *
     * <p>Em aberto é o que ainda está vencido na agenda: como atender uma revisão empurra
     * {@code next_review_on} para frente, o que continua no passado é exatamente o que foi ignorado.
     *
     * <p>As duas pontas recortam pela mesma régua — só entra o que <em>venceu</em> dentro da janela.
     * Sem isso a conta fica assimétrica e mente para cima: limpar parte de um backlog antigo somaria
     * ao numerador, enquanto o resto do backlog, vencido antes da janela, ficaria de fora do
     * denominador — e um dia em que se atendeu 1 de 5 atrasadas apareceria como 100%.
     */
    private MetricsDtos.SrsAdherence srsAdherence(
            Long userId, List<DrillLog> drills, LocalDate windowStart, LocalDate today) {

        Set<String> attended = new HashSet<>();
        for (DrillLog drill : drills) {
            if (drill.isWasDue() && !drill.getDrilledOn().isAfter(today) && dueInWindow(drill, windowStart)) {
                attended.add(drill.getNodeId() + "@" + drill.getDrilledOn());
            }
        }

        long outstanding = srsRepository.findByIdUserIdAndNextReviewOnLessThanEqual(userId, today).stream()
                .map(SrsReview::getNextReviewOn)
                .filter(dueOn -> !dueOn.isBefore(windowStart))
                .count();

        return MetricsDtos.SrsAdherence.of(
                attended.size(), attended.size() + outstanding, TARGET_SRS_ADHERENCE_PERCENT);
    }

    /** A revisão que este drill atendeu venceu dentro da janela? */
    private boolean dueInWindow(DrillLog drill, LocalDate windowStart) {
        return drill.getDueOn() != null && !drill.getDueOn().isBefore(windowStart);
    }

    /** Nós concluídos na janela — o currículo acompanha o que aparece na aula? */
    private long nodesCompleted(Long userId, LocalDate windowStart, LocalDate today) {
        return progressRepository.findByIdUserId(userId).stream()
                .filter(progress -> progress.getStatus() == ProgressStatus.COMPLETED)
                .map(UserProgress::getCompletedAt)
                .filter(completedAt -> completedAt != null && inWindow(completedAt, windowStart, today))
                .count();
    }

    /**
     * Nós cujo quiz foi refeito depois de já ter sido aprovado — sinal de retenção, não de streak.
     *
     * <p>"Mais de uma tentativa" não serve como definição: reprovar e passar na segunda é o caminho
     * normal para concluir um nó, e contá-lo acenderia a meta no primeiro erro de quem só está
     * avançando. O que docs/05 chama de espontâneo é voltar a um quiz que já estava resolvido — daí
     * só contar tentativa posterior à primeira aprovação.
     *
     * <p>O histórico é lido inteiro, e não só a janela, porque a aprovação que torna a tentativa uma
     * repetição costuma ser bem anterior a ela; a janela recorta a repetição, não a aprovação.
     */
    private long quizRetakes(Long userId, LocalDate windowStart, LocalDate today) {
        List<QuizAttempt> attempts = quizAttemptRepository.findByUserIdOrderByAttemptedOnAscIdAsc(userId);

        Set<Long> passedNodes = new HashSet<>();
        Set<Long> retaken = new HashSet<>();

        for (QuizAttempt attempt : attempts) {
            if (attempt.getAttemptedOn().isAfter(today)) {
                continue;
            }
            boolean afterFirstPass = passedNodes.contains(attempt.getNodeId());
            if (afterFirstPass && !attempt.getAttemptedOn().isBefore(windowStart)) {
                retaken.add(attempt.getNodeId());
            }
            if (attempt.isPassed()) {
                passedNodes.add(attempt.getNodeId());
            }
        }
        return retaken.size();
    }

    /** {@code completed_at} é instante; a janela é em dias, no mesmo fuso que define "hoje". */
    private boolean inWindow(Instant instant, LocalDate windowStart, LocalDate today) {
        LocalDate date = instant.atZone(clock.getZone()).toLocalDate();
        return !date.isBefore(windowStart) && !date.isAfter(today);
    }
}
