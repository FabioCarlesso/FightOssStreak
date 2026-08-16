/**
 * Repetição espaçada (SM-2 adaptado).
 *
 * O backend é a fonte da verdade do agendamento (`SrsScheduler.java`). Esta implementação existe
 * para preview otimista na UI — mostrar "volta em 5 dias" no momento em que o usuário escolhe a
 * auto-avaliação, sem esperar a resposta da API.
 *
 * As duas precisam concordar: os casos de `srs.test.ts` são os mesmos de `SrsSchedulerTest.java`.
 * Divergência aqui não é detalhe estético — faz a UI mentir sobre quando a técnica volta.
 */
import { addDays, type IsoDate } from './streak.ts';

/** Auto-avaliação do drill. */
export type Recall = 'FORGOT' | 'HARD' | 'OK' | 'EASY';

/** Nota 0–5 do SM-2 correspondente a cada auto-avaliação. */
export const RECALL_QUALITY: Readonly<Record<Recall, number>> = Object.freeze({
  FORGOT: 2,
  HARD: 3,
  OK: 4,
  EASY: 5,
});

/** Piso do fator de facilidade, como no SM-2 original. */
export const MIN_EASE_FACTOR = 1.3;

/** Fator de facilidade inicial. */
export const DEFAULT_EASE_FACTOR = 2.5;

export interface SrsState {
  readonly repetitions: number;
  readonly intervalDays: number;
  readonly easeFactor: number;
  readonly nextReviewOn: IsoDate;
}

/** Primeiro agendamento, feito quando o nó é concluído: revisar no dia seguinte. */
export function initialSchedule(today: IsoDate): SrsState {
  return {
    repetitions: 0,
    intervalDays: 1,
    easeFactor: DEFAULT_EASE_FACTOR,
    nextReviewOn: addDays(today, 1),
  };
}

/**
 * Aplica uma revisão e devolve o próximo agendamento.
 *
 * @param current estado atual, ou `null` se o nó ainda não estava agendado
 */
export function review(current: SrsState | null, recall: Recall, today: IsoDate): SrsState {
  const base: SrsState = current ?? {
    repetitions: 0,
    intervalDays: 0,
    easeFactor: DEFAULT_EASE_FACTOR,
    nextReviewOn: today,
  };

  const quality = RECALL_QUALITY[recall];
  const easeFactor = adjustEaseFactor(base.easeFactor, quality);

  let repetitions: number;
  let intervalDays: number;

  if (quality < 3) {
    // Lapso: esqueceu a técnica, então ela volta amanhã e a contagem recomeça.
    repetitions = 0;
    intervalDays = 1;
  } else {
    repetitions = base.repetitions + 1;
    intervalDays =
      repetitions === 1
        ? 2
        : repetitions === 2
          ? 5
          : Math.max(1, Math.round(base.intervalDays * easeFactor));
  }

  return { repetitions, intervalDays, easeFactor, nextReviewOn: addDays(today, intervalDays) };
}

/** Fórmula de ajuste do SM-2: acertos fáceis aumentam o fator, difíceis reduzem. */
function adjustEaseFactor(easeFactor: number, quality: number): number {
  const adjusted = easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
  return Math.max(MIN_EASE_FACTOR, adjusted);
}

/** Um nó está vencido quando a data agendada já chegou. */
export function isDue(state: Pick<SrsState, 'nextReviewOn'>, today: IsoDate): boolean {
  return state.nextReviewOn <= today;
}
