package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.StreakFreezeRepository;
import dev.fos.web.dto.ActivityDtos;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta a visão de streak a partir do log de drills. */
@Service
public class StreakQueryService {

    /** Meta declarada no critério de sucesso do MVP: 12 dias com registro em 30. */
    static final int TARGET_ACTIVE_DAYS_30 = 12;

    private static final int WINDOW_DAYS = 30;

    private final DrillLogRepository drillLogRepository;
    private final StreakFreezeRepository streakFreezeRepository;
    private final StreakFreezeWriter streakFreezeWriter;
    private final StreakService streakService;
    private final FosProperties properties;
    private final Clock clock;

    public StreakQueryService(
            DrillLogRepository drillLogRepository,
            StreakFreezeRepository streakFreezeRepository,
            StreakFreezeWriter streakFreezeWriter,
            StreakService streakService,
            FosProperties properties,
            Clock clock) {
        this.drillLogRepository = drillLogRepository;
        this.streakFreezeRepository = streakFreezeRepository;
        this.streakFreezeWriter = streakFreezeWriter;
        this.streakService = streakService;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Streak, recorde, dias ativos e saldo de freeze.
     *
     * <p>Escreve, e por isso não é {@code readOnly}: um dia perdido perdoado precisa virar linha em
     * {@code streak_freeze} no momento em que é percebido, senão o saldo do mês seria recalculado
     * do zero em toda leitura e a corrente que quebrou devolveria os freezes que gastou. A gravação
     * é idempotente pela chave única {@code (user_id, covered_on)} — o cálculo já não recobra dia
     * que está no histórico, e o {@link StreakFreezeWriter} faz a restrição ser rede e não mina
     * quando duas requisições da mesma conta chegam juntas — duas abas abertas bastam, e sem ele a
     * que perdia a corrida respondia 500. No mesmo movimento saem as linhas dos dias que
     * <b>deixaram</b> de ser dia perdido: registrar o treino de ontem ({@code drilledOn}) é caminho
     * normal do app, e devolve o freeze que aquele dia tinha gasto.
     *
     * <p>Não existe job diário por trás disso: o perdão é derivado do {@code drill_log} na leitura
     * seguinte, que é quando alguém tem o que ver. Quem passou dois meses fora não perde saldo
     * naquele intervalo — não havia corrente para salvar.
     */
    @Transactional
    public ActivityDtos.StreakView streak(Long userId, LocalDate today) {
        List<LocalDate> days = drillLogRepository.findDistinctDrillDates(userId);
        int budget = properties.streak().freezesPerMonth();

        StreakService.FrozenStreak frozen =
                streakService.resolveWithFreeze(
                        days, today, streakFreezeRepository.findCoveredDates(userId), budget);

        for (LocalDate covered : frozen.newlyFrozenDays()) {
            streakFreezeWriter.registrar(userId, covered, clock.instant());
        }
        if (!frozen.releasedDays().isEmpty()) {
            streakFreezeRepository.deleteByUserIdAndCoveredOnIn(userId, frozen.releasedDays());
        }

        return new ActivityDtos.StreakView(
                frozen.currentStreak(),
                streakService.longestStreak(days),
                days.contains(today),
                streakService.activeDaysInWindow(days, today, WINDOW_DAYS),
                TARGET_ACTIVE_DAYS_30,
                today,
                budget,
                frozen.freezesRemaining(),
                frozen.frozenDays().isEmpty() ? null : frozen.frozenDays().get(0));
    }
}
