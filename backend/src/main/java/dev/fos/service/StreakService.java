package dev.fos.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
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
     * Dias com registro dentro da janela recente.
     *
     * <p>É a métrica que realmente responde ao critério de sucesso do MVP — "≥ 12 dias de 30" —
     * enquanto o streak responde só a "manteve a corrente" (docs/05-mvp-web-plano.md).
     */
    public int activeDaysInWindow(Collection<LocalDate> drillDates, LocalDate today, int windowDays) {
        LocalDate from = today.minusDays(windowDays - 1L);
        return (int) List.copyOf(new TreeSet<>(drillDates)).stream()
                .filter(day -> !day.isBefore(from) && !day.isAfter(today))
                .count();
    }
}
