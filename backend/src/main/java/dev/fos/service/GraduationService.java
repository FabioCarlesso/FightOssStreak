package dev.fos.service;

import dev.fos.model.GraduationGoal;
import dev.fos.repo.AttireCount;
import dev.fos.repo.GraduationGoalRepository;
import dev.fos.repo.TrainingSessionRepository;
import dev.fos.web.dto.GraduationDtos;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Próximo grau (#193, D75): quantas aulas o diário já registrou desde o último grau.
 *
 * <p>A contagem é <b>derivada a cada leitura</b>, como o streak: nada aqui grava número de aula.
 * Corrigir a data ou o tipo de uma sessão no diário muda a contagem na leitura seguinte, sem
 * segunda verdade para reconciliar.
 */
@Service
public class GraduationService {

    private final GraduationGoalRepository goals;
    private final TrainingSessionRepository sessions;
    private final Clock clock;

    public GraduationService(
            GraduationGoalRepository goals, TrainingSessionRepository sessions, Clock clock) {
        this.goals = goals;
        this.sessions = sessions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GraduationDtos.GraduationView get(Long userId) {
        return goals.findById(userId)
                .map(goal -> view(userId, goal))
                .orElseGet(() -> new GraduationDtos.GraduationView(null, null));
    }

    /** Cria ou reescreve a meta. É uma linha por conta: trocar de grau é mudar a data. */
    @Transactional
    public GraduationDtos.GraduationView save(
            Long userId, GraduationDtos.GoalRequest request, LocalDate today) {
        if (request.lastGraduationOn().isAfter(today)) {
            throw new IllegalArgumentException(
                    "A data do último grau não pode ser futura: " + request.lastGraduationOn());
        }
        GraduationGoal goal = goals.findById(userId).orElseGet(() -> new GraduationGoal(userId));
        goal.setLastGraduationOn(request.lastGraduationOn());
        goal.setCurrentLabel(trimToNull(request.currentLabel()));
        goal.setNextLabel(trimToNull(request.nextLabel()));
        goal.setMinTotal(request.minTotal());
        goal.setMinGi(request.minGi());
        goal.setMinNogi(request.minNogi());
        goal.touch(clock.instant());
        return view(userId, goals.save(goal));
    }

    /** Tira o card da home. As sessões ficam: a meta é só a régua, não o registro. */
    @Transactional
    public void delete(Long userId) {
        goals.deleteById(userId);
    }

    private GraduationDtos.GraduationView view(Long userId, GraduationGoal goal) {
        return new GraduationDtos.GraduationView(
                new GraduationDtos.GoalView(
                        goal.getLastGraduationOn(),
                        goal.getCurrentLabel(),
                        goal.getNextLabel(),
                        goal.getMinTotal(),
                        goal.getMinGi(),
                        goal.getMinNogi()),
                count(sessions.countClassesSince(userId, goal.getLastGraduationOn())));
    }

    /** Espelho do {@code countClassesSince} de {@code shared/domain} (D17). */
    static GraduationDtos.ClassCount count(List<AttireCount> linhas) {
        int gi = 0;
        int nogi = 0;
        int semMarcacao = 0;
        for (AttireCount linha : linhas) {
            int n = linha.total().intValue();
            if (linha.attire() == null) {
                semMarcacao += n;
            } else {
                switch (linha.attire()) {
                    case GI -> gi += n;
                    case NOGI -> nogi += n;
                }
            }
        }
        return new GraduationDtos.ClassCount(gi + nogi + semMarcacao, gi, nogi, semMarcacao);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String limpo = value.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
