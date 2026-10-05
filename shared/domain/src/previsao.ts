import { review, type Recall } from './srs.ts';
import type { IsoDate } from './streak.ts';

/** O que a tela sabe do agendamento atual de um nó — o `SrsView` da API, sem depender dele. */
export interface ScheduleSnapshot {
  readonly scheduled?: boolean;
  readonly nextReviewOn?: string;
  readonly repetitions?: number;
  readonly intervalDays?: number;
}

/**
 * Prévia de "volta em N dias" para a auto-avaliação escolhida, pela mesma regra SM-2 do backend.
 *
 * Serve para a tela responder no toque, antes do round-trip. Sem o fator de facilidade persistido
 * (a API não o expõe), a prévia só é confiável nas primeiras repetições: depois disso devolve
 * `null`, porque não prometer número é melhor que prometer errado. Mora aqui desde a #141 para o web
 * e o app mostrarem o mesmo número.
 */
export function previewIntervalDays(
  srs: ScheduleSnapshot | undefined,
  recall: Recall,
  today: IsoDate,
): number | null {
  const current =
    srs?.scheduled && srs.nextReviewOn
      ? {
          repetitions: srs.repetitions ?? 0,
          intervalDays: srs.intervalDays ?? 0,
          easeFactor: 2.5,
          nextReviewOn: srs.nextReviewOn,
        }
      : null;

  if (current && current.repetitions > 2) return null;

  return review(current, recall, today).intervalDays;
}
