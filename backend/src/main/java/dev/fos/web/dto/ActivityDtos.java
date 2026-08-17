package dev.fos.web.dto;

import dev.fos.model.Belt;
import dev.fos.model.ProgressStatus;
import dev.fos.model.Recall;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/** Streak, registro de drill, agenda de revisão e aceite do disclaimer. */
public final class ActivityDtos {

    private ActivityDtos() {}

    /**
     * @param activeDaysLast30 dias com registro nos últimos 30 — a métrica do critério de sucesso
     * @param targetDaysLast30 meta declarada em docs/05-mvp-web-plano.md (12 de 30)
     */
    public record StreakView(
            int currentStreak,
            int longestStreak,
            boolean drilledToday,
            int activeDaysLast30,
            int targetDaysLast30,
            LocalDate today) {}

    /**
     * @param drilledOn data do treino; ausente = hoje. Permite registrar o treino de ontem sem
     *     falsear a data, que é o que aconteceria se o registro fosse só "agora".
     */
    public record DrillRequest(
            @NotNull Recall recall, @Size(max = 1000) String note, LocalDate drilledOn) {}

    public record DrillResult(
            String nodeCode,
            LocalDate drilledOn,
            ProgressStatus status,
            LocalDate nextReviewOn,
            int intervalDays,
            StreakView streak) {}

    /** Agenda de "o que drillar hoje" — a razão de o app existir, segundo docs/00. */
    public record ReviewAgenda(LocalDate today, int dueCount, List<DueItemView> due) {}

    /**
     * @param daysOverdue dias de atraso; ordena a lista, porque nó mais atrasado é mais urgente
     */
    public record DueItemView(
            String nodeCode,
            String title,
            Belt belt,
            String moduleCode,
            LocalDate nextReviewOn,
            long daysOverdue,
            Integer lastQuizScore) {}

    /**
     * @param acceptedVersion versão já aceita, se houver
     * @param currentVersion versão vigente do texto; se diferir, o aviso é reexibido
     */
    public record DisclaimerStatus(
            boolean accepted, String acceptedVersion, String currentVersion, String shortNotice) {}

    public record AcceptDisclaimerRequest(@NotNull String version) {}
}
