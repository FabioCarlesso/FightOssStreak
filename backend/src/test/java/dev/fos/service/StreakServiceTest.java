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

    // ---- Freeze de streak (#99, D55). Os mesmos casos existem em shared/domain (D17). ----

    private static final int FREEZES = 2;

    @Test
    @DisplayName("um dia perdido com saldo mantém a corrente e cobra um freeze")
    void freezeCoversMissedDay() {
        // Treinou 14 e 16; faltou o dia 15. Sem freeze a corrente valeria 1.
        List<LocalDate> days = List.of(TODAY, TODAY.minusDays(2), TODAY.minusDays(3));

        StreakService.FrozenStreak result =
                service.resolveWithFreeze(days, TODAY, List.of(), FREEZES);

        assertThat(result.currentStreak()).isEqualTo(3);
        assertThat(result.frozenDays()).containsExactly(TODAY.minusDays(1));
        assertThat(result.newlyFrozenDays()).containsExactly(TODAY.minusDays(1));
        assertThat(result.freezesUsedThisMonth()).isEqualTo(1);
        assertThat(result.freezesRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("sem saldo o comportamento é o de antes: a corrente para no buraco")
    void withoutBalanceTheStreakBreaks() {
        List<LocalDate> gasto = List.of(TODAY.minusDays(1), TODAY.minusDays(4));
        // Faltaram 15 e 12 (já perdoados) e 10 — para o terceiro não há mais saldo no mês.
        List<LocalDate> days =
                List.of(
                        TODAY,
                        TODAY.minusDays(2),
                        TODAY.minusDays(3),
                        TODAY.minusDays(5),
                        TODAY.minusDays(7));

        StreakService.FrozenStreak result = service.resolveWithFreeze(days, TODAY, gasto, FREEZES);

        assertThat(result.currentStreak()).isEqualTo(4);
        assertThat(result.newlyFrozenDays()).isEmpty();
        assertThat(result.freezesRemaining()).isZero();
    }

    @Test
    @DisplayName("com saldo zero nada é perdoado e a corrente vale o que valia")
    void zeroBudgetKeepsOldBehaviour() {
        List<LocalDate> days = List.of(TODAY, TODAY.minusDays(2));

        StreakService.FrozenStreak result = service.resolveWithFreeze(days, TODAY, List.of(), 0);

        assertThat(result.currentStreak()).isEqualTo(service.currentStreak(days, TODAY));
        assertThat(result.frozenDays()).isEmpty();
        assertThat(result.freezesRemaining()).isZero();
    }

    @Test
    @DisplayName("o saldo renova no mês seguinte, e o mês do dia perdido é quem paga")
    void balanceRenewsEachMonth() {
        LocalDate setembro = LocalDate.of(2026, 9, 2);
        List<LocalDate> gastoDeAgosto =
                List.of(LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 12));
        List<LocalDate> days =
                List.of(setembro, LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 30));

        StreakService.FrozenStreak result =
                service.resolveWithFreeze(days, setembro, gastoDeAgosto, FREEZES);

        assertThat(result.currentStreak()).isEqualTo(3);
        assertThat(result.frozenDays()).containsExactly(LocalDate.of(2026, 9, 1));
        assertThat(result.freezesUsedThisMonth()).isEqualTo(1);
        assertThat(result.freezesRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("recalcular não cobra de novo o dia que já está no histórico")
    void recalculationIsIdempotent() {
        List<LocalDate> days = List.of(TODAY, TODAY.minusDays(2), TODAY.minusDays(3));

        StreakService.FrozenStreak primeira =
                service.resolveWithFreeze(days, TODAY, List.of(), FREEZES);
        StreakService.FrozenStreak segunda =
                service.resolveWithFreeze(days, TODAY, primeira.newlyFrozenDays(), FREEZES);

        assertThat(segunda.newlyFrozenDays()).isEmpty();
        assertThat(segunda.currentStreak()).isEqualTo(primeira.currentStreak());
        assertThat(segunda.freezesUsedThisMonth()).isEqualTo(1);
    }

    @Test
    @DisplayName("ontem sem treino é perdoado, e hoje nunca gasta freeze")
    void todayNeverSpendsAFreeze() {
        // Ainda não treinou hoje: o dia não acabou e não pode ser cobrado.
        List<LocalDate> days = List.of(TODAY.minusDays(2), TODAY.minusDays(3));

        StreakService.FrozenStreak result =
                service.resolveWithFreeze(days, TODAY, List.of(), FREEZES);

        assertThat(result.currentStreak()).isEqualTo(2);
        assertThat(result.frozenDays()).containsExactly(TODAY.minusDays(1));
        assertThat(result.freezesUsedThisMonth()).isEqualTo(1);
    }

    @Test
    @DisplayName("não perdoa dias anteriores ao primeiro treino")
    void neverFreezesBeforeTheFirstDrill() {
        StreakService.FrozenStreak result =
                service.resolveWithFreeze(List.of(TODAY.minusDays(2)), TODAY, List.of(), FREEZES);

        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.frozenDays()).containsExactly(TODAY.minusDays(1));
    }

    @Test
    @DisplayName("dia perdoado que ganha registro depois devolve o freeze")
    void aBackdatedDrillReleasesTheFreeze() {
        // `drilledOn` permite registrar o treino de ontem, e é caminho normal do app. Cobrar por um
        // dia que acabou tendo treino faria abrir a home de manhã custar saldo.
        List<LocalDate> days = List.of(TODAY, TODAY.minusDays(1), TODAY.minusDays(2));

        StreakService.FrozenStreak result =
                service.resolveWithFreeze(days, TODAY, List.of(TODAY.minusDays(1)), FREEZES);

        assertThat(result.currentStreak()).isEqualTo(3);
        assertThat(result.releasedDays()).containsExactly(TODAY.minusDays(1));
        assertThat(result.frozenDays()).isEmpty();
        assertThat(result.freezesUsedThisMonth()).isZero();
        assertThat(result.freezesRemaining()).isEqualTo(FREEZES);
    }

    @Test
    @DisplayName("corrente que morre por falta de saldo não leva junto os freezes que gastou")
    void aDeadStreakDoesNotSpendTheBalance() {
        // Único registro é de seis dias atrás: nenhum freeze salva essa corrente, e nenhum é
        // cobrado.
        StreakService.FrozenStreak result =
                service.resolveWithFreeze(
                        List.of(TODAY.minusDays(6)), TODAY, List.of(TODAY.minusDays(1)), FREEZES);

        assertThat(result.currentStreak()).isZero();
        // O de ontem já estava gasto e continua gasto; nenhum novo foi debitado.
        assertThat(result.freezesUsedThisMonth()).isEqualTo(1);
        assertThat(result.freezesRemaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("dias ativos na janela de 30 dias ignoram registros mais antigos")
    void activeDaysWindowIsBounded() {
        List<LocalDate> days =
                List.of(TODAY, TODAY.minusDays(29), TODAY.minusDays(30), TODAY.minusDays(60));

        assertThat(service.activeDaysInWindow(days, TODAY, 30)).isEqualTo(2);
    }
}
