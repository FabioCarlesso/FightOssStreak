package dev.fos.service;

import dev.fos.model.Recall;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Agendamento por repetição espaçada, SM-2 adaptado (o algoritmo do Anki).
 *
 * <p>Diferença importante em relação ao SM-2 clássico: aqui a revisão agendada não é releitura de
 * teoria, é <em>drill</em> no tatame. Por isso os intervalos iniciais são curtos — uma técnica
 * vista na segunda precisa voltar antes do fim da semana, senão some.
 *
 * <p>Esta classe é a fonte da verdade do agendamento. {@code shared/domain/srs.ts} replica o mesmo
 * cálculo apenas para preview otimista na UI; divergência entre os dois é bug, e há teste dos dois
 * lados sobre os mesmos casos.
 */
@Service
public class SrsScheduler {

    /**
     * Piso do fator de facilidade, como no SM-2 original: abaixo disso o intervalo nunca cresce.
     */
    static final double MIN_EASE_FACTOR = 1.3;

    /** Fator de facilidade inicial. */
    static final double DEFAULT_EASE_FACTOR = 2.5;

    /** Estado do agendamento de um nó. */
    public record State(
            int repetitions, int intervalDays, double easeFactor, LocalDate nextReviewOn) {}

    /** Primeiro agendamento, feito quando o nó é concluído: revisar no dia seguinte. */
    public State initial(LocalDate today) {
        return new State(0, 1, DEFAULT_EASE_FACTOR, today.plusDays(1));
    }

    /**
     * Aplica uma revisão e devolve o próximo agendamento.
     *
     * @param current estado atual, ou {@code null} se o nó ainda não estava agendado
     * @param recall auto-avaliação do drill
     * @param today data da revisão
     */
    public State review(State current, Recall recall, LocalDate today) {
        State base = current != null ? current : new State(0, 0, DEFAULT_EASE_FACTOR, today);

        int quality = recall.quality();
        double easeFactor = adjustEaseFactor(base.easeFactor(), quality);

        int repetitions;
        int intervalDays;
        if (quality < 3) {
            // Lapso: volta ao começo. Esqueceu a técnica, então ela precisa voltar amanhã.
            repetitions = 0;
            intervalDays = 1;
        } else {
            repetitions = base.repetitions() + 1;
            intervalDays =
                    switch (repetitions) {
                        case 1 -> 2;
                        case 2 -> 5;
                        default -> (int) Math.max(1, Math.round(base.intervalDays() * easeFactor));
                    };
        }

        return new State(repetitions, intervalDays, easeFactor, today.plusDays(intervalDays));
    }

    /** Fórmula de ajuste do SM-2: acertos fáceis aumentam o fator, difíceis reduzem. */
    private double adjustEaseFactor(double easeFactor, int quality) {
        double adjusted = easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        return Math.max(MIN_EASE_FACTOR, adjusted);
    }
}
