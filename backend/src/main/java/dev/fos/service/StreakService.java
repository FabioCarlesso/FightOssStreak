package dev.fos.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Cálculo de streak a partir das datas com registro de drill.
 *
 * <p>Decisão de produto embutida aqui: o streak <em>não</em> quebra no mesmo dia em que você ainda
 * não treinou. Ele conta a partir de hoje se houve registro hoje, ou a partir de ontem caso
 * contrário. Sem isso, o streak apareceria zerado toda manhã — punindo o usuário por ainda não ter
 * ido treinar e transformando a mecânica em ansiedade em vez de reforço.
 */
@Service
public class StreakService {

    /** Streak atual em dias consecutivos com registro. */
    public int currentStreak(Collection<LocalDate> drillDates, LocalDate today) {
        TreeSet<LocalDate> days = new TreeSet<>(drillDates);
        if (days.isEmpty()) {
            return 0;
        }

        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        if (!days.contains(cursor)) {
            return 0;
        }

        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    /** Maior sequência já alcançada — usado para dar contexto ao streak atual. */
    public int longestStreak(Collection<LocalDate> drillDates) {
        TreeSet<LocalDate> days = new TreeSet<>(drillDates);
        if (days.isEmpty()) {
            return 0;
        }

        int longest = 1;
        int running = 1;
        LocalDate previous = null;
        for (LocalDate day : days) {
            if (previous != null) {
                running = ChronoUnit.DAYS.between(previous, day) == 1 ? running + 1 : 1;
                longest = Math.max(longest, running);
            }
            previous = day;
        }
        return longest;
    }

    /**
     * Um dia perdido perdoado por um freeze (#99, D55).
     *
     * @param currentStreak dias com <em>treino</em> na corrente atual; dia coberto por freeze
     *     mantém a corrente e não conta
     * @param frozenDays dias da corrente atual cobertos por freeze, do mais recente ao mais antigo
     * @param newlyFrozenDays os que ainda não estavam no histórico recebido — é o que gravar
     * @param releasedDays dias do histórico que deixaram de ser dia perdido, porque ganharam
     *     registro depois de perdoados — é o que apagar, e o saldo já vem devolvido
     * @param freezesUsedThisMonth freezes gastos no mês de {@code today}, inclusive os de correntes
     *     que já quebraram
     * @param freezesRemaining o que sobra do saldo do mês, nunca negativo
     */
    public record FrozenStreak(
            int currentStreak,
            List<LocalDate> frozenDays,
            List<LocalDate> newlyFrozenDays,
            List<LocalDate> releasedDays,
            int freezesUsedThisMonth,
            int freezesRemaining) {}

    /**
     * Streak com perdão de dias perdidos (#99, D55).
     *
     * <p>Um dia sem registro consome um freeze do mês <em>daquele dia</em> e a corrente segue. Sem
     * saldo, o comportamento é o de sempre: a corrente para ali. <b>Hoje nunca gasta freeze</b> — o
     * dia ainda não acabou, e é a mesma razão que faz {@link #currentStreak} ancorar em ontem.
     *
     * <p>O histórico já gravado entra por parâmetro, e não é redescoberto a cada chamada, porque um
     * freeze gasto <b>continua gasto</b> depois que a corrente que ele salvava quebrou — sem isso o
     * saldo seria "por corrente" e não "por mês", e bastaria deixar a sequência morrer para ganhar
     * freeze novo. Pela mesma razão o cálculo é idempotente: dia que já está no histórico não é
     * cobrado de novo, e por isso o método pode rodar em toda leitura do streak.
     *
     * <p>Freeze nunca cobre dia anterior ao primeiro treino: quem começou anteontem não deve nada
     * aos dias em que ainda nem usava o app.
     *
     * <p>Dia perdoado que <b>ganha registro depois</b> devolve o freeze. Registrar o treino de
     * ontem é caminho normal do app — {@code drilledOn} existe para isso —, e cobrar por um dia que
     * acabou tendo treino faria abrir a home de manhã custar saldo. O que a tabela guarda é "dia
     * sem treino que foi perdoado", e um dia com treino deixou de ser isso.
     *
     * <p>Espelha {@code resolveStreakWithFreeze} em {@code shared/domain} (D17).
     */
    public FrozenStreak resolveWithFreeze(
            Collection<LocalDate> drillDates,
            LocalDate today,
            Collection<LocalDate> consumedFreezes,
            int freezesPerMonth) {

        int budget = Math.max(0, freezesPerMonth);
        TreeSet<LocalDate> days = new TreeSet<>(drillDates);

        Set<LocalDate> alreadyFrozen = new HashSet<>();
        List<LocalDate> releasedDays = new ArrayList<>();
        Map<YearMonth, Integer> spent = new HashMap<>();
        for (LocalDate day : new TreeSet<>(consumedFreezes)) {
            if (days.contains(day)) {
                releasedDays.add(day);
                continue;
            }
            alreadyFrozen.add(day);
            spent.merge(YearMonth.from(day), 1, Integer::sum);
        }

        List<LocalDate> frozenDays = new ArrayList<>();
        List<LocalDate> newlyFrozenDays = new ArrayList<>();
        int streak = 0;

        if (!days.isEmpty()) {
            LocalDate earliest = days.first();
            LocalDate cursor = days.contains(today) ? today : today.minusDays(1);

            // Buraco só é cobrado quando a caminhada alcança OUTRO dia de treino do outro lado
            // dele. Sem isso, uma corrente que morre por falta de saldo levaria junto os freezes
            // que gastou tentando sobreviver — a pessoa perderia a sequência e o saldo do mês na
            // mesma virada.
            List<LocalDate> pending = new ArrayList<>();
            List<LocalDate> pendingCharged = new ArrayList<>();

            while (!cursor.isBefore(earliest)) {
                if (days.contains(cursor)) {
                    streak++;
                    frozenDays.addAll(pending);
                    newlyFrozenDays.addAll(pendingCharged);
                    pending.clear();
                    pendingCharged.clear();
                } else if (alreadyFrozen.contains(cursor)) {
                    // Já está no histórico: entra na corrente sem custo novo.
                    pending.add(cursor);
                } else {
                    YearMonth month = YearMonth.from(cursor);
                    int used = spent.getOrDefault(month, 0);
                    if (used >= budget) {
                        break;
                    }
                    spent.put(month, used + 1);
                    pending.add(cursor);
                    pendingCharged.add(cursor);
                }
                cursor = cursor.minusDays(1);
            }

            // A caminhada parou sem outro dia de treino do outro lado: devolve o debitado à toa.
            for (LocalDate day : pendingCharged) {
                spent.merge(YearMonth.from(day), -1, Integer::sum);
            }
        }

        int usedThisMonth = spent.getOrDefault(YearMonth.from(today), 0);
        return new FrozenStreak(
                streak,
                List.copyOf(frozenDays),
                List.copyOf(newlyFrozenDays),
                List.copyOf(releasedDays),
                usedThisMonth,
                Math.max(0, budget - usedThisMonth));
    }

    /**
     * Dias com registro dentro da janela recente.
     *
     * <p>É a métrica que realmente responde ao critério de sucesso do MVP — "≥ 12 dias de 30" —
     * enquanto o streak responde só a "manteve a corrente" (docs/05-mvp-web-plano.md).
     */
    public int activeDaysInWindow(
            Collection<LocalDate> drillDates, LocalDate today, int windowDays) {
        LocalDate from = today.minusDays(windowDays - 1L);
        return (int)
                List.copyOf(new TreeSet<>(drillDates)).stream()
                        .filter(day -> !day.isBefore(from) && !day.isAfter(today))
                        .count();
    }
}
