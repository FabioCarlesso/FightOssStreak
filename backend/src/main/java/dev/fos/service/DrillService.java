package dev.fos.service;

import dev.fos.model.DrillLog;
import dev.fos.model.Node;
import dev.fos.model.ProgressStatus;
import dev.fos.model.SrsReview;
import dev.fos.model.UserNodeKey;
import dev.fos.model.UserProgress;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.QuizQuestionRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.repo.UserProgressRepository;
import dev.fos.web.dto.ActivityDtos;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de "treinei isso hoje".
 *
 * <p>Um drill faz três coisas de uma vez: entra no log (alimenta o streak), reagenda o nó no SRS e,
 * em nós sem quiz, marca conclusão.
 */
@Service
public class DrillService {

    private final DrillLogRepository drillLogRepository;
    private final SrsReviewRepository srsRepository;
    private final UserProgressRepository progressRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final CurriculumQueryService curriculumQueryService;
    private final SrsScheduler srsScheduler;
    private final StreakQueryService streakQueryService;
    private final Clock clock;

    public DrillService(
            DrillLogRepository drillLogRepository,
            SrsReviewRepository srsRepository,
            UserProgressRepository progressRepository,
            QuizQuestionRepository quizQuestionRepository,
            CurriculumQueryService curriculumQueryService,
            SrsScheduler srsScheduler,
            StreakQueryService streakQueryService,
            Clock clock) {
        this.drillLogRepository = drillLogRepository;
        this.srsRepository = srsRepository;
        this.progressRepository = progressRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.curriculumQueryService = curriculumQueryService;
        this.srsScheduler = srsScheduler;
        this.streakQueryService = streakQueryService;
        this.clock = clock;
    }

    @Transactional
    public ActivityDtos.DrillResult log(
            Long userId, String nodeCode, ActivityDtos.DrillRequest request, LocalDate today) {

        Node node = curriculumQueryService.requireNode(nodeCode);
        LocalDate drilledOn = request.drilledOn() != null ? request.drilledOn() : today;
        if (drilledOn.isAfter(today)) {
            throw new IllegalArgumentException("Não dá para registrar drill em data futura: " + drilledOn);
        }

        UserNodeKey key = new UserNodeKey(userId, node.getId());

        // Lido ANTES do reagendamento: é o reagendamento que apaga o estado anterior da agenda,
        // e é esse estado que responde se o drill atendeu a uma revisão sugerida pelo SRS
        // (critério de sucesso em docs/05-mvp-web-plano.md).
        SrsReview scheduled = srsRepository.findById(key).orElse(null);
        LocalDate dueOn = scheduled != null ? scheduled.getNextReviewOn() : null;
        boolean wasDue = dueOn != null && !dueOn.isAfter(drilledOn);

        drillLogRepository.save(new DrillLog(
                userId,
                node.getId(),
                drilledOn,
                request.recall(),
                request.note(),
                wasDue,
                dueOn,
                clock.instant()));

        SrsReview review = reschedule(scheduled, key, request, drilledOn);
        ProgressStatus status = advanceProgress(userId, node, key);

        return new ActivityDtos.DrillResult(
                node.getCode(),
                drilledOn,
                status,
                review.getNextReviewOn(),
                review.getIntervalDays(),
                streakQueryService.streak(userId, today));
    }

    private SrsReview reschedule(
            SrsReview existing, UserNodeKey key, ActivityDtos.DrillRequest request, LocalDate drilledOn) {
        SrsScheduler.State current = existing == null
                ? null
                : new SrsScheduler.State(
                        existing.getRepetitions(),
                        existing.getIntervalDays(),
                        existing.getEaseFactor(),
                        existing.getNextReviewOn());

        SrsScheduler.State next = srsScheduler.review(current, request.recall(), drilledOn);

        SrsReview review = existing != null
                ? existing
                : new SrsReview(
                        key, next.nextReviewOn(), next.intervalDays(), next.easeFactor(), next.repetitions());
        review.setNextReviewOn(next.nextReviewOn());
        review.setIntervalDays(next.intervalDays());
        review.setEaseFactor(next.easeFactor());
        review.setRepetitions(next.repetitions());
        review.setLastReviewedOn(drilledOn);
        return srsRepository.save(review);
    }

    /**
     * Nó sem quiz é concluído pelo drill.
     *
     * <p>Se a conclusão dependesse só do quiz, os módulos sem quiz escrito ainda travariam a árvore
     * inteira — e a curadoria de quiz é incremental por decisão (M0 e M1 primeiro). Onde há quiz, ele
     * continua sendo o que conclui: drill sozinho só move o nó para IN_PROGRESS.
     */
    private ProgressStatus advanceProgress(Long userId, Node node, UserNodeKey key) {
        UserProgress progress = progressRepository
                .findById(key)
                .orElseGet(() -> new UserProgress(key, ProgressStatus.IN_PROGRESS, clock.instant()));

        boolean hasQuiz = !quizQuestionRepository.findByNodeIdOrderByOrderIndexAsc(node.getId()).isEmpty();

        if (progress.getStatus() != ProgressStatus.COMPLETED) {
            progress.setStatus(hasQuiz ? ProgressStatus.IN_PROGRESS : ProgressStatus.COMPLETED);
            if (!hasQuiz && progress.getCompletedAt() == null) {
                progress.setCompletedAt(clock.instant());
            }
        }
        progress.setUpdatedAt(clock.instant());
        progressRepository.save(progress);
        return progress.getStatus();
    }
}
