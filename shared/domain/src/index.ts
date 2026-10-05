/**
 * Regras de negócio puras, sem dependência de UI nem de rede — e, desde a #141, o texto e os rótulos
 * que o web e o app precisam mostrar iguais (aviso, recall, diário).
 *
 * É o que mais se paga na migração para React Native: streak, agendamento de SRS e lógica de
 * desbloqueio são idênticos em web e mobile (docs/arquitetura.md).
 */
export {
  addDays,
  daysBetween,
  currentStreak,
  longestStreak,
  activeDaysInWindow,
  resolveStreakWithFreeze,
} from './streak.ts';
export type { FrozenStreak, IsoDate } from './streak.ts';

export { buildStreakHeatmap, heatLevel, weekdayOf } from './heatmap.ts';
export type { ActivityDay, Heatmap, HeatmapCell, HeatLevel } from './heatmap.ts';

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

export { FULL_DISCLAIMER, SHORT_DISCLAIMER } from './disclaimer.ts';

export {
  RECALL_LABELS,
  recallLabel,
  SESSION_KIND_LABELS,
  sessionKindLabel,
  FEELING_LABELS,
  feelingLabel,
} from './rotulos.ts';

export { formatDay, todayIso, monthRange, emptySessionFields, toSessionBody } from './diario.ts';
export type { SessionFieldsState } from './diario.ts';

export { previewIntervalDays } from './previsao.ts';
export type { ScheduleSnapshot } from './previsao.ts';
