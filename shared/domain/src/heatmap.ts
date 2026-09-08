/**
 * Grade do heatmap de streak (#102).
 *
 * O que entra aqui é **calendário**, não regra nova: quais dias tiveram registro é decisão do
 * backend (D58 — sessões que não são `DESCANSO` mais os drills avulsos), e o que falta é dispor
 * esses dias em colunas de sete para a tela desenhar. Fica em `shared/domain` pelo mesmo motivo do
 * `streak.ts`: é conta de data pura, sem `Date` local e sem fuso, e a migração para React Native
 * reaproveita a grade inteira trocando só o desenho.
 *
 * **Não existe um segundo conjunto de dias.** O heatmap lê exatamente o que a corrente conta — se
 * um dia aparece pintado aqui e não sustenta o streak, o app passou a ter duas verdades sobre o
 * mesmo fato, que é o defeito que a D58 foi fechar.
 */
import { addDays, type IsoDate } from './streak.ts';

/** Um dia com registro, como o backend o devolve. `count` é quantos registros, não quantas horas. */
export interface ActivityDay {
  readonly day: IsoDate;
  /** Sessões que contam como treino mais drills avulsos naquele dia. Nunca zero na entrada. */
  readonly count: number;
  /** Dia **sem** treino que um freeze perdoou (D55). Nunca vem junto de `count > 0`. */
  readonly frozen: boolean;
}

/**
 * Intensidade de um dia, já em degraus.
 *
 * Três degraus e não cinco: o histórico típico é de um ou dois registros por dia, e uma escala mais
 * fina pintaria quase tudo do mesmo tom — a diferença que a pessoa lê é "treinei / treinei muito".
 */
export type HeatLevel = 0 | 1 | 2 | 3;

export interface HeatmapCell {
  readonly day: IsoDate;
  readonly count: number;
  readonly frozen: boolean;
  readonly level: HeatLevel;
}

export interface Heatmap {
  /** Primeiro dia da grade — sempre um domingo, para as colunas fecharem. */
  readonly from: IsoDate;
  /** Último dia com significado: `today`. O resto da última coluna é preenchimento. */
  readonly to: IsoDate;
  /** Colunas de sete dias, domingo em cima. `null` é dia futuro dentro da última coluna. */
  readonly weeks: ReadonlyArray<ReadonlyArray<HeatmapCell | null>>;
  /** Dias com treino no período — o número que o rótulo acessível anuncia. */
  readonly activeDays: number;
  /** Dias perdoados por freeze no período. */
  readonly frozenDays: number;
}

/** Dia da semana (0 = domingo) sem envolver fuso: a data é de calendário, como no `streak.ts`. */
export function weekdayOf(date: IsoDate): number {
  return new Date(`${date}T00:00:00Z`).getUTCDay();
}

/** Zero registros é dia vazio; daí em diante, um degrau por registro até o teto. */
export function heatLevel(count: number): HeatLevel {
  if (count <= 0) return 0;
  if (count === 1) return 1;
  if (count === 2) return 2;
  return 3;
}

/**
 * Monta a grade que a tela desenha.
 *
 * A última coluna é a semana de `today`, e os dias posteriores a ele ficam `null` em vez de virarem
 * célula vazia: dia que ainda não chegou não é dia sem treino, e pintá-lo igual a uma falta diria
 * que a pessoa já falhou no sábado que vem.
 *
 * `weeks` colunas cobrem `weeks * 7` dias contados para trás a partir do sábado da semana corrente
 * — por isso o período começa sempre num domingo, e é ele que sai em `from`.
 */
export function buildStreakHeatmap(
  activity: readonly ActivityDay[],
  today: IsoDate,
  weeks: number,
): Heatmap {
  const columns = Math.max(1, Math.trunc(weeks));
  const lastSaturday = addDays(today, 6 - weekdayOf(today));
  const from = addDays(lastSaturday, -(columns * 7 - 1));

  const porDia = new Map<IsoDate, ActivityDay>();
  for (const dia of activity) {
    porDia.set(dia.day, dia);
  }

  const grade: (HeatmapCell | null)[][] = [];
  let activeDays = 0;
  let frozenDays = 0;

  for (let coluna = 0; coluna < columns; coluna += 1) {
    const semana: (HeatmapCell | null)[] = [];
    for (let linha = 0; linha < 7; linha += 1) {
      const day = addDays(from, coluna * 7 + linha);
      if (day > today) {
        semana.push(null);
        continue;
      }
      const registro = porDia.get(day);
      const count = registro?.count ?? 0;
      const frozen = registro?.frozen ?? false;
      if (count > 0) activeDays += 1;
      if (frozen) frozenDays += 1;
      semana.push({ day, count, frozen, level: heatLevel(count) });
    }
    grade.push(semana);
  }

  return { from, to: today, weeks: grade, activeDays, frozenDays };
}
