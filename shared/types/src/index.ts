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
export type DemoSession = Schemas['DemoSessionView'];

export type MvpMetrics = Schemas['MvpMetrics'];
export type CountedMetric = Schemas['Counted'];
export type SrsAdherence = Schemas['SrsAdherence'];

export type AccountView = Schemas['AccountView'];
/** `ADMIN` ou `USUARIO` (D48). É por ele que a web decide o que mostrar de administração. */
export type Role = NonNullable<AccountView['role']>;
export type AuthProviderView = Schemas['AuthProviderView'];
export type AuthProviders = Schemas['AuthProviders'];
/**
 * Se um link de e-mail ainda vale, e por que não vale (D47).
 *
 * Um tipo para os dois links: confirmação e redefinição são conferidas do mesmo jeito, e pelo
 * mesmo motivo — abrir a URL não pode gastá-la.
 */
export type LinkStatus = Schemas['LinkView'];

/**
 * As contas do sistema, vistas por quem administra (#89, #90).
 *
 * É a primeira resposta do app que carrega dado pessoal de outras pessoas — o que entra nela está
 * registrado em `docs/11-privacidade.md`.
 */
export type AdminUserView = Schemas['AdminUserView'];
export type AdminUserPage = Schemas['AdminUserPage'];
/** Estado de acesso da conta: `APROVADO` usa o app, `RECUSADO` está bloqueada (#90). */
export type AccessStatus = NonNullable<AdminUserView['accessStatus']>;

/**
 * O painel de uso do app (#85, D50).
 *
 * Agregado e de ninguém: não há um campo aqui que identifique uma pessoa, e essa ausência é o que
 * mantém verdadeiro o desenho de privacidade da coleta — ver `docs/11-privacidade.md`.
 */
export type PanelView = Schemas['PanelView'];
export type PanelAccessSeries = Schemas['AccessSeries'];
export type PanelAccessPoint = Schemas['AccessPoint'];
export type PanelFunnelStep = Schemas['FunnelStep'];
export type PanelSlice = Schemas['Slice'];
export type PanelProfile = Schemas['Profile'];
export type PanelAccountTotals = Schemas['AccountTotals'];

/**
 * O que a coleta de uso manda a cada mudança de rota (#84, D50).
 *
 * A lista é curta porque o servidor não confia no cliente: dispositivo, navegador, sistema,
 * idioma e país são derivados da própria requisição, e os eventos de funil são emitidos pelo
 * backend. O que o navegador manda é só o que só ele sabe.
 */
export type UsageEventRequest = Schemas['EventRequest'];

export type FeedbackRequest = Schemas['FeedbackRequest'];
export type FeedbackView = Schemas['FeedbackView'];
export type FeedbackList = Schemas['FeedbackList'];
export type FeedbackStatusRequest = Schemas['FeedbackStatusRequest'];
/** Assunto do feedback — o que o formulário oferece para escolher. */
export type FeedbackCategory = NonNullable<FeedbackRequest['category']>;
/** Estado de um feedback na fila do dono. */
export type FeedbackStatus = NonNullable<FeedbackStatusRequest['status']>;

export type { components, paths } from '../generated/api.ts';
