package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreakServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 16);

    private final StreakService service = new StreakService();

    @Test
    @DisplayName("sem registro nenhum, streak é zero")
    void emptyHistory() {
        assertThat(service.currentStreak(Set.of(), TODAY)).isZero();
        assertThat(service.longestStreak(Set.of())).isZero();
    }

    @Test
    @DisplayName("dias consecutivos terminando hoje contam integralmente")
    void consecutiveDaysEndingToday() {
        List<LocalDate> days = List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));

        assertThat(service.currentStreak(days, TODAY)).isEqualTo(3);
    }

    @Test
    @DisplayName("o streak não quebra durante o dia em que ainda não se treinou")
    void streakSurvivesUntilEndOfDay() {
        // Treinou até ontem; hoje ainda não foi ao tatame. Zerar aqui puniria o usuário
        // por ainda não ter treinado — a mecânica viraria ansiedade em vez de reforço.
        List<LocalDate> days = List.of(TODAY.minusDays(1), TODAY.minusDays(2));

        assertThat(service.currentStreak(days, TODAY)).isEqualTo(2);
    }

    @Test
    @DisplayName("dois dias sem registro quebram o streak")
    void gapBreaksStreak() {
        List<LocalDate> days = List.of(TODAY.minusDays(2), TODAY.minusDays(3));

        assertThat(service.currentStreak(days, TODAY)).isZero();
    }

    @Test
    @DisplayName("datas repetidas no mesmo dia contam uma vez só")
    void duplicateDatesCountOnce() {
        List<LocalDate> days = List.of(TODAY, TODAY, TODAY.minusDays(1));

        assertThat(service.currentStreak(days, TODAY)).isEqualTo(2);
    }

    @Test
    @DisplayName("o recorde considera qualquer janela do histórico, não só a atual")
    void longestStreakLooksAtWholeHistory() {
        List<LocalDate> days =
                List.of(
                        TODAY,
                        TODAY.minusDays(10),
                        TODAY.minusDays(11),
                        TODAY.minusDays(12),
                        TODAY.minusDays(13));

        assertThat(service.currentStreak(days, TODAY)).isEqualTo(1);
        assertThat(service.longestStreak(days)).isEqualTo(4);
    }

    @Test
    @DisplayName("dias ativos na janela de 30 dias ignoram registros mais antigos")
    void activeDaysWindowIsBounded() {
        List<LocalDate> days =
                List.of(TODAY, TODAY.minusDays(29), TODAY.minusDays(30), TODAY.minusDays(60));

        assertThat(service.activeDaysInWindow(days, TODAY, 30)).isEqualTo(2);
    }
}
