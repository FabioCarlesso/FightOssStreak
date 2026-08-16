package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.Recall;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Casos espelhados em {@code shared/domain/src/srs.test.ts} — as duas implementações precisam
 * concordar, senão o preview da UI mente sobre quando o nó volta.
 */
class SrsSchedulerTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);

    private final SrsScheduler scheduler = new SrsScheduler();

    @Test
    @DisplayName("concluir um nó agenda o primeiro drill para o dia seguinte")
    void firstScheduleIsTomorrow() {
        SrsScheduler.State state = scheduler.initial(TODAY);

        assertThat(state.nextReviewOn()).isEqualTo(TODAY.plusDays(1));
        assertThat(state.repetitions()).isZero();
        assertThat(state.easeFactor()).isEqualTo(SrsScheduler.DEFAULT_EASE_FACTOR);
    }

    @Test
    @DisplayName("os intervalos crescem 2 → 5 → escalados pelo fator de facilidade")
    void intervalsGrowAcrossSuccessfulReviews() {
        SrsScheduler.State first = scheduler.review(null, Recall.OK, TODAY);
        assertThat(first.intervalDays()).isEqualTo(2);
        assertThat(first.repetitions()).isEqualTo(1);

        SrsScheduler.State second = scheduler.review(first, Recall.OK, TODAY.plusDays(2));
        assertThat(second.intervalDays()).isEqualTo(5);

        // Terceira revisão em diante o intervalo escala: round(5 * 2.5) = 13 dias.
        // O valor é fixado aqui e em shared/domain/src/srs.test.ts para travar a paridade
        // entre as duas implementações.
        SrsScheduler.State third = scheduler.review(second, Recall.OK, TODAY.plusDays(7));
        assertThat(third.intervalDays()).isEqualTo(13);
        assertThat(third.nextReviewOn()).isEqualTo(TODAY.plusDays(7).plusDays(13));
    }

    @Test
    @DisplayName("esquecer zera as repetições e traz a técnica de volta amanhã")
    void forgettingResetsSchedule() {
        SrsScheduler.State grown = scheduler.review(
                scheduler.review(null, Recall.EASY, TODAY), Recall.EASY, TODAY.plusDays(2));

        SrsScheduler.State lapsed = scheduler.review(grown, Recall.FORGOT, TODAY.plusDays(7));

        assertThat(lapsed.repetitions()).isZero();
        assertThat(lapsed.intervalDays()).isEqualTo(1);
        assertThat(lapsed.nextReviewOn()).isEqualTo(TODAY.plusDays(8));
    }

    @Test
    @DisplayName("drills difíceis reduzem o fator de facilidade; fáceis aumentam")
    void easeFactorRespondsToRecall() {
        SrsScheduler.State hard = scheduler.review(null, Recall.HARD, TODAY);
        SrsScheduler.State easy = scheduler.review(null, Recall.EASY, TODAY);

        assertThat(hard.easeFactor()).isLessThan(SrsScheduler.DEFAULT_EASE_FACTOR);
        assertThat(easy.easeFactor()).isGreaterThan(SrsScheduler.DEFAULT_EASE_FACTOR);
    }

    @Test
    @DisplayName("o fator de facilidade nunca cai abaixo do piso do SM-2")
    void easeFactorHasFloor() {
        SrsScheduler.State state = scheduler.review(null, Recall.HARD, TODAY);
        for (int i = 0; i < 20; i++) {
            state = scheduler.review(state, Recall.HARD, TODAY.plusDays(i + 1L));
        }

        assertThat(state.easeFactor()).isGreaterThanOrEqualTo(SrsScheduler.MIN_EASE_FACTOR);
    }

    @Test
    @DisplayName("o intervalo nunca é menor que um dia")
    void intervalNeverCollapses() {
        SrsScheduler.State state = scheduler.review(null, Recall.HARD, TODAY);
        for (int i = 0; i < 10; i++) {
            state = scheduler.review(state, Recall.HARD, TODAY.plusDays(i + 1L));
            assertThat(state.intervalDays()).isGreaterThanOrEqualTo(1);
        }
    }
}
