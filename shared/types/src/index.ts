/**
 * Aliases estáveis sobre os tipos gerados do OpenAPI.
 *
 * O arquivo `generated/api.ts` é regenerado por `npm run gen:types` e não deve ser editado. Este
 * arquivo existe para que o resto do código importe `NodeDetail` em vez de
 * `components['schemas']['NodeDetailView']` — assim uma renomeação no backend quebra em um lugar só.
 */
import type { components } from '../generated/api.ts';

type Schemas = components['schemas'];

export type TreeView = Schemas['TreeView'];
export type ModuleView = Schemas['ModuleView'];
export type NodeSummary = Schemas['NodeSummaryView'];
export type NodeDetail = Schemas['NodeDetailView'];
export type ProgressSummary = Schemas['ProgressSummary'];
export type PrereqView = Schemas['PrereqView'];
export type VideoView = Schemas['VideoView'];
export type ExtraVideoView = Schemas['ExtraVideoView'];
export type SrsView = Schemas['SrsView'];
export type DrillEntry = Schemas['DrillEntryView'];
export type PinnedNoteRequest = Schemas['PinnedNoteRequest'];
export type PinnedNote = Schemas['PinnedNoteView'];

export type QuizQuestionView = Schemas['QuestionView'];
export type QuizOptionView = Schemas['OptionView'];
export type QuizSubmission = Schemas['QuizSubmission'];
export type QuizResult = Schemas['QuizResult'];
export type QuestionFeedback = Schemas['QuestionFeedback'];

export type StreakView = Schemas['StreakView'];
export type DrillRequest = Schemas['DrillRequest'];
export type DrillResult = Schemas['DrillResult'];
export type ReviewAgenda = Schemas['ReviewAgenda'];
export type DueItem = Schemas['DueItemView'];
export type DisclaimerStatus = Schemas['DisclaimerStatus'];

export type MvpMetrics = Schemas['MvpMetrics'];
export type CountedMetric = Schemas['Counted'];
export type SrsAdherence = Schemas['SrsAdherence'];

export type { components, paths } from '../generated/api.ts';
