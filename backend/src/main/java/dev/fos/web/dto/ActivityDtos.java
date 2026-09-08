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
     * @param currentStreak dias com treino na corrente atual; dia coberto por freeze mantém a
     *     corrente e não conta
     * @param activeDaysLast30 dias com registro nos últimos 30 — a métrica do critério de sucesso
     * @param targetDaysLast30 meta declarada em docs/05-mvp-web-plano.md (12 de 30)
     * @param freezesPerMonth saldo cheio do mês (#99, D55); zero = perdão desligado nesta
     *     instalação, e a tela não mostra nada sobre freeze
     * @param freezesRemaining o que sobra do saldo no mês corrente
     * @param lastFrozenOn dia mais recente da corrente atual coberto por freeze; {@code null} =
     *     nenhum. É o que responde "um freeze foi consumido" sem a tela precisar de outra chamada
     */
    public record StreakView(
            int currentStreak,
            int longestStreak,
            boolean drilledToday,
            int activeDaysLast30,
            int targetDaysLast30,
            LocalDate today,
            int freezesPerMonth,
            int freezesRemaining,
            LocalDate lastFrozenOn) {}

    /**
     * Histórico de dias com registro, para o heatmap da home (#102).
     *
     * <p>Devolve <b>só os dias que têm algo</b>, e não o período inteiro: a grade é desenhada pela
     * tela a partir de {@code from}/{@code to}, e mandar cento e oitenta linhas para acender vinte
     * seria carregar o vazio pela rede. O período é fechado nas duas pontas e {@code to} é sempre
     * hoje — dia que ainda não chegou não é dia sem treino.
     *
     * @param days em ordem crescente de data, sem buracos representados
     */
    public record StreakHistory(LocalDate from, LocalDate to, List<HistoryDay> days) {}

    /**
     * Um dia do heatmap.
     *
     * <p>O conjunto é <b>o mesmo</b> que a corrente conta (D58): sessões que não são {@code
     * DESCANSO} mais os drills avulsos. Não há segunda regra de "dia ativo" — se houvesse, o
     * heatmap poderia acender um dia que o streak ignora, e o app passaria a afirmar duas coisas
     * diferentes sobre o mesmo dia na mesma tela.
     *
     * @param count quantos registros naquele dia; é o que vira intensidade na tela, e nunca é zero
     *     em dia com treino
     * @param frozen dia <b>sem</b> treino que um freeze perdoou (D55). Nunca vem com {@code count >
     *     0}: uma linha em {@code streak_freeze} significa sempre dia sem treino
     */
    public record HistoryDay(LocalDate day, int count, boolean frozen) {}

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
