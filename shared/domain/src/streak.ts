/**
 * Cálculo de streak a partir das datas com registro de drill.
 *
 * Datas são strings `YYYY-MM-DD` — o mesmo formato que a API devolve. Usar `Date` aqui traria
 * fuso horário para dentro de uma regra que é puramente de calendário, e "que dia é hoje" passaria
 * a depender de onde o usuário está.
 */

/** Data de calendário no formato `YYYY-MM-DD`. */
export type IsoDate = string;

const DAY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function assertIsoDate(value: IsoDate): void {
  if (!DAY_PATTERN.test(value)) {
    throw new Error(`Data fora do formato YYYY-MM-DD: ${value}`);
  }
}

/** Soma dias a uma data de calendário, sem envolver fuso horário. */
export function addDays(date: IsoDate, days: number): IsoDate {
  assertIsoDate(date);
  const utc = new Date(`${date}T00:00:00Z`);
  utc.setUTCDate(utc.getUTCDate() + days);
  return utc.toISOString().slice(0, 10);
}

/** Diferença em dias entre duas datas de calendário (`to - from`). */
export function daysBetween(from: IsoDate, to: IsoDate): number {
  assertIsoDate(from);
  assertIsoDate(to);
  const millis = Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`);
  return Math.round(millis / 86_400_000);
}

/**
 * Streak atual em dias consecutivos.
 *
 * O streak **não** quebra durante o dia em que ainda não se treinou: conta a partir de hoje se
 * houve registro hoje, ou a partir de ontem caso contrário. Sem isso o contador apareceria zerado
 * toda manhã, punindo o usuário por ainda não ter ido ao tatame.
 *
 * Espelha `StreakService.currentStreak` no backend.
 */
export function currentStreak(drillDates: readonly IsoDate[], today: IsoDate): number {
  const days = new Set(drillDates);
  if (days.size === 0) return 0;

  let cursor = days.has(today) ? today : addDays(today, -1);
  if (!days.has(cursor)) return 0;

  let streak = 0;
  while (days.has(cursor)) {
    streak += 1;
    cursor = addDays(cursor, -1);
  }
  return streak;
}

/** Maior sequência já alcançada no histórico. */
export function longestStreak(drillDates: readonly IsoDate[]): number {
  const days = [...new Set(drillDates)].sort();
  if (days.length === 0) return 0;

  let longest = 1;
  let running = 1;
  for (let i = 1; i < days.length; i += 1) {
    running = daysBetween(days[i - 1]!, days[i]!) === 1 ? running + 1 : 1;
    longest = Math.max(longest, running);
  }
  return longest;
}

/**
 * Dias com registro dentro da janela recente.
 *
 * É a métrica que responde ao critério de sucesso do MVP (≥ 12 dias em 30), enquanto o streak
 * responde só a "manteve a corrente".
 */
export function activeDaysInWindow(
  drillDates: readonly IsoDate[],
  today: IsoDate,
  windowDays: number,
): number {
  const from = addDays(today, -(windowDays - 1));
  return [...new Set(drillDates)].filter((day) => day >= from && day <= today).length;
}
