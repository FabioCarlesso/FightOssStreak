package dev.fos.web.dto;

import java.time.LocalDate;

/**
 * Os quatro critérios de sucesso do MVP, medidos (docs/05-mvp-web-plano.md).
 *
 * <p>Cada métrica viaja junto com a própria meta. Número solto não decide nada: o que o documento
 * pede é uma resposta de atingiu/não atingiu ao fim dos 30 dias — inclusive a resposta ruim, que é
 * o critério de falha honesto declarado lá.
 */
public final class MetricsDtos {

    private MetricsDtos() {}

    /**
     * @param windowStart primeiro dia considerado, inclusive
     * @param windowEnd último dia considerado, inclusive — o "hoje" do relógio da aplicação
     * @param targetsFor30Days as metas valem para a janela de 30 dias de docs/05; janela diferente
     *     mede o mesmo, mas a comparação com a meta deixa de fazer sentido
     */
    public record MvpMetrics(
            LocalDate windowStart,
            LocalDate windowEnd,
            int windowDays,
            boolean targetsFor30Days,
            Counted daysWithDrill,
            SrsAdherence srsAdherence,
            Counted nodesCompleted,
            Counted quizRetakes) {}

    /** Contagem simples comparada a uma meta. */
    public record Counted(long value, long target, boolean met) {

        public static Counted of(long value, long target) {
            return new Counted(value, target, value >= target);
        }
    }

    /**
     * Aderência à sugestão do SRS: das revisões que venceram na janela, quantas foram atendidas.
     *
     * @param attended revisões vencidas que receberam drill (um nó por dia conta uma vez)
     * @param scheduled total de revisões vencidas na janela — atendidas mais em aberto
     * @param percent {@code null} quando nada venceu na janela; sem agenda não há aderência a
     *     medir, e contar isso como 0% acusaria falha onde não houve cobrança
     */
    public record SrsAdherence(
            long attended, long scheduled, Integer percent, int targetPercent, boolean met) {

        public static SrsAdherence of(long attended, long scheduled, int targetPercent) {
            if (scheduled == 0) {
                return new SrsAdherence(0, 0, null, targetPercent, false);
            }
            int percent = Math.round((attended * 100f) / scheduled);
            return new SrsAdherence(
                    attended, scheduled, percent, targetPercent, percent >= targetPercent);
        }
    }
}
