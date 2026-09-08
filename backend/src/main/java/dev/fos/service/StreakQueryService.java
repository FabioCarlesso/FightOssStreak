package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.repo.DayCount;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.StreakFreezeRepository;
import dev.fos.repo.TrainingSessionRepository;
import dev.fos.web.dto.ActivityDtos;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monta a visão de streak a partir do que a conta registrou.
 *
 * <p>Desde a #114 o insumo são <b>duas</b> listas — as sessões do diário que contam como treino e
 * os drills avulsos —, e continua sendo <b>uma</b> corrente e <b>um</b> livro-caixa (D58). Duas
 * correntes disputando o mesmo saldo criariam a pergunta "qual delas gastou o freeze" sem resposta
 * possível.
 */
@Service
public class StreakQueryService {

    /** Meta declarada no critério de sucesso do MVP: 12 dias com registro em 30. */
    static final int TARGET_ACTIVE_DAYS_30 = 12;

    private static final int WINDOW_DAYS = 30;

    /** Janela padrão do heatmap: 26 semanas, ~6 meses — o teto que a issue #102 pediu. */
    static final int HISTORY_DEFAULT_DAYS = 182;

    /**
     * Teto do período pedido.
     *
     * <p>Existe para o parâmetro não virar consulta arbitrária: um ano é o horizonte que qualquer
     * grade de heatmap ainda desenha, e acima disso a resposta cresceria sem ninguém ver diferença.
     */
    static final int HISTORY_MAX_DAYS = 366;

    private final DrillLogRepository drillLogRepository;
    private final TrainingSessionRepository trainingSessionRepository;
    private final StreakFreezeRepository streakFreezeRepository;
    private final StreakFreezeWriter streakFreezeWriter;
    private final StreakService streakService;
    private final FosProperties properties;
    private final Clock clock;

    public StreakQueryService(
            DrillLogRepository drillLogRepository,
            TrainingSessionRepository trainingSessionRepository,
            StreakFreezeRepository streakFreezeRepository,
            StreakFreezeWriter streakFreezeWriter,
            StreakService streakService,
            FosProperties properties,
            Clock clock) {
        this.drillLogRepository = drillLogRepository;
        this.trainingSessionRepository = trainingSessionRepository;
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
        List<LocalDate> days = diasComRegistro(userId);
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

    /**
     * Histórico de dias com registro para o heatmap da home (#102).
     *
     * <p>É <b>leitura</b>, e por isso {@code readOnly} — ao contrário de {@link #streak}, que
     * grava. Os dias perdoados saem do livro-caixa como ele está: quem escreve nele é o cálculo do
     * streak, e duplicar essa escrita aqui daria duas rotas gravando a mesma linha sem necessidade.
     * Na prática isso significa que um dia recém-perdoado só aparece marcado no heatmap depois que
     * {@code GET /api/streak} passou por ele — e é a tela que garante a ordem, pedindo o histórico
     * depois do streak.
     *
     * <p>O conjunto de dias é o <b>mesmo</b> do streak, e é isso que impede uma segunda verdade
     * (D58): sessões que não são {@code DESCANSO} mais os drills avulsos. A diferença é que aqui a
     * contagem por dia é agregada no banco, e não em memória — histórico longo é o caso que a issue
     * pede para não degradar, e o índice {@code (user_id, data)} das duas tabelas cobre o recorte.
     */
    @Transactional(readOnly = true)
    public ActivityDtos.StreakHistory history(Long userId, LocalDate today, Integer dias) {
        int janela =
                dias == null ? HISTORY_DEFAULT_DAYS : Math.min(HISTORY_MAX_DAYS, Math.max(1, dias));
        LocalDate from = today.minusDays(janela - 1L);

        Map<LocalDate, Integer> registrosPorDia = new TreeMap<>();
        for (DayCount linha : trainingSessionRepository.countTrainingByDay(userId, from, today)) {
            registrosPorDia.merge(linha.dia(), linha.total().intValue(), Integer::sum);
        }
        for (DayCount linha : drillLogRepository.countStandaloneDrillsByDay(userId, from, today)) {
            registrosPorDia.merge(linha.dia(), linha.total().intValue(), Integer::sum);
        }

        // Um dia perdoado é, por definição, dia SEM treino: se ele aparecer nos registros acima, a
        // linha de freeze já saiu (ou vai sair) pelo caminho do streak, e quem manda é o registro.
        Set<LocalDate> perdoados =
                streakFreezeRepository.findCoveredDates(userId).stream()
                        .filter(dia -> !dia.isBefore(from) && !dia.isAfter(today))
                        .filter(dia -> !registrosPorDia.containsKey(dia))
                        .collect(Collectors.toCollection(TreeSet::new));

        List<ActivityDtos.HistoryDay> days = new ArrayList<>();
        for (LocalDate dia : new TreeSet<>(concat(registrosPorDia.keySet(), perdoados))) {
            days.add(
                    new ActivityDtos.HistoryDay(
                            dia, registrosPorDia.getOrDefault(dia, 0), perdoados.contains(dia)));
        }

        return new ActivityDtos.StreakHistory(from, today, List.copyOf(days));
    }

    private static Set<LocalDate> concat(Set<LocalDate> um, Set<LocalDate> outro) {
        Set<LocalDate> todos = new LinkedHashSet<>(um);
        todos.addAll(outro);
        return todos;
    }

    /**
     * Os dias que a corrente conta: sessão que não é {@code DESCANSO}, mais drill avulso (D58).
     *
     * <p>Antes da #114 era só o {@code drill_log}, e com o diário na rotina isso mostraria corrente
     * morta para quem treinou seis dias na semana — o app mentindo sobre a rotina de quem usa, que
     * é o oposto do que a D55 foi consertar. Drill vinculado a sessão não entra por aqui porque a
     * sessão dele já responde por aquele dia.
     *
     * <p>É conjunto e não lista: o mesmo dia pode ter sessão e drill avulso, e a corrente conta
     * dias, não registros. Duas sessões no mesmo dia também contam um dia só, pela mesma razão.
     */
    private List<LocalDate> diasComRegistro(Long userId) {
        Set<LocalDate> dias =
                new LinkedHashSet<>(trainingSessionRepository.findDistinctTrainingDates(userId));
        dias.addAll(drillLogRepository.findDistinctStandaloneDrillDates(userId));
        return new ArrayList<>(dias);
    }
}
