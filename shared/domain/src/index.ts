/**
 * Regras de negócio puras, sem dependência de UI nem de rede.
 *
 * É o que mais se paga na migração para React Native: streak, agendamento de SRS e lógica de
 * desbloqueio são idênticos em web e mobile (docs/03-estrutura-projeto.md).
 */
export { addDays, daysBetween, currentStreak, longestStreak, activeDaysInWindow } from './streak.ts';
export type { IsoDate } from './streak.ts';

export {
  initialSchedule,
  review,
  isDue,
  RECALL_QUALITY,
  MIN_EASE_FACTOR,
  DEFAULT_EASE_FACTOR,
} from './srs.ts';
export type { Recall, SrsState } from './srs.ts';

export { isUnlocked, missingPrereqs, resolveStatuses } from './unlock.ts';
export type { GraphNode, ProgressStatus, UnlockRule } from './unlock.ts';
