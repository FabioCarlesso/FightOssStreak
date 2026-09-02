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

/**
 * Um dia perdido perdoado por um freeze.
 *
 * O saldo é por **mês de calendário** — o mesmo recorte que a pessoa usa para pensar em "este mês
 * viajei". Janela deslizante de 30 dias seria mais justa na média e impossível de explicar em uma
 * linha na tela, que é onde essa informação precisa caber.
 */
export interface FrozenStreak {
  /** Dias com treino na corrente atual. Dia coberto por freeze mantém a corrente e **não** conta. */
  readonly currentStreak: number;
  /** Dias da corrente atual cobertos por freeze, do mais recente para o mais antigo. */
  readonly frozenDays: readonly IsoDate[];
  /** Os que ainda não estavam no histórico recebido — é o que a persistência precisa gravar. */
  readonly newlyFrozenDays: readonly IsoDate[];
  /**
   * Dias do histórico que **deixaram** de ser dia perdido: ganharam registro depois de perdoados.
   * É o que a persistência precisa apagar, e o saldo já vem devolvido aqui.
   */
  readonly releasedDays: readonly IsoDate[];
  readonly freezesUsedThisMonth: number;
  readonly freezesRemaining: number;
}

function monthOf(date: IsoDate): string {
  return date.slice(0, 7);
}

/**
 * Streak com perdão de dias perdidos (#99).
 *
 * Um dia sem registro consome um freeze do mês daquele dia e a corrente segue. Sem saldo, o
 * comportamento é o de sempre: a corrente para ali. Hoje nunca gasta freeze — o dia ainda não
 * acabou, e é a mesma razão que faz `currentStreak` ancorar em ontem.
 *
 * `consumedFreezes` é o histórico já gravado. Ele entra por parâmetro, e não é redescoberto a cada
 * chamada, porque um freeze gasto continua gasto mesmo depois que a corrente que ele salvava
 * quebrou — sem isso o saldo seria "por corrente", não "por mês", e bastaria deixar a sequência
 * morrer para ganhar freeze novo. Pela mesma razão a função é idempotente: dia que já está no
 * histórico não é cobrado de novo.
 *
 * Freeze nunca cobre dia anterior ao primeiro treino: quem começou anteontem não deve nada aos
 * dias em que ainda nem usava o app.
 *
 * Dia perdoado que **ganha registro depois** devolve o freeze (`releasedDays`). Registrar o treino
 * de ontem é caminho normal do app — `drilledOn` existe para isso —, e cobrar por um dia que
 * acabou tendo treino faria abrir a home de manhã custar saldo. O que a tabela guarda é "dia sem
 * treino que foi perdoado", e um dia com treino deixou de ser isso.
 *
 * Espelha `StreakService.resolveWithFreeze` no backend.
 */
export function resolveStreakWithFreeze(
  drillDates: readonly IsoDate[],
  today: IsoDate,
  consumedFreezes: readonly IsoDate[],
  freezesPerMonth: number,
): FrozenStreak {
  assertIsoDate(today);
  const budget = Math.max(0, Math.trunc(freezesPerMonth));

  const days = new Set(drillDates);

  const alreadyFrozen = new Set<IsoDate>();
  const releasedDays: IsoDate[] = [];
  const spent = new Map<string, number>();
  for (const day of consumedFreezes) {
    assertIsoDate(day);
    if (alreadyFrozen.has(day) || releasedDays.includes(day)) continue;
    if (days.has(day)) {
      releasedDays.push(day);
      continue;
    }
    alreadyFrozen.add(day);
    spent.set(monthOf(day), (spent.get(monthOf(day)) ?? 0) + 1);
  }

  const frozenDays: IsoDate[] = [];
  const newlyFrozenDays: IsoDate[] = [];
  let streak = 0;

  if (days.size > 0) {
    // Comparação lexicográfica basta: `YYYY-MM-DD` ordena como data.
    const earliest = [...days].sort()[0]!;
    let cursor = days.has(today) ? today : addDays(today, -1);

    // Buraco só é cobrado quando a caminhada alcança OUTRO dia de treino do outro lado dele. Sem
    // isso, uma corrente que morre por falta de saldo levaria junto os freezes que gastou tentando
    // sobreviver — a pessoa perderia a sequência e o saldo do mês na mesma virada.
    let pending: IsoDate[] = [];
    let pendingCharged: IsoDate[] = [];

    while (cursor >= earliest) {
      if (days.has(cursor)) {
        streak += 1;
        frozenDays.push(...pending);
        newlyFrozenDays.push(...pendingCharged);
        pending = [];
        pendingCharged = [];
      } else if (alreadyFrozen.has(cursor)) {
        // Já está no histórico: entra na corrente sem custo novo.
        pending.push(cursor);
      } else {
        const month = monthOf(cursor);
        const used = spent.get(month) ?? 0;
        if (used >= budget) break;
        spent.set(month, used + 1);
        pending.push(cursor);
        pendingCharged.push(cursor);
      }
      cursor = addDays(cursor, -1);
    }

    // A caminhada parou sem outro dia de treino do outro lado: devolve o que foi debitado à toa.
    for (const day of pendingCharged) {
      const month = monthOf(day);
      spent.set(month, (spent.get(month) ?? 1) - 1);
    }
  }

  const usedThisMonth = spent.get(monthOf(today)) ?? 0;
  return {
    currentStreak: streak,
    frozenDays,
    newlyFrozenDays,
    releasedDays,
    freezesUsedThisMonth: usedThisMonth,
    freezesRemaining: Math.max(0, budget - usedThisMonth),
  };
}
