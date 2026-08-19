/**
 * Cliente da API do FightOssStreak.
 *
 * Fica em `shared/` porque é reaproveitado integralmente na migração para React Native — só a
 * camada de UI é reescrita (docs/03-estrutura-projeto.md).
 */
import type {
  DisclaimerStatus,
  DrillRequest,
  DrillResult,
  MvpMetrics,
  NodeDetail,
  PinnedNote,
  QuizResult,
  QuizSubmission,
  ReviewAgenda,
  StreakView,
  TreeView,
} from '@fos/types';

export interface ApiClientOptions {
  /** Base da API. Em dev o Vite faz proxy de `/api`, então o padrão relativo basta. */
  readonly baseUrl?: string;
  readonly fetch?: typeof globalThis.fetch;
}

/** Erro de API com o código estruturado que o backend devolve. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** O nó ainda não tem quiz escrito — estado esperado, não falha. */
  get isQuizUnavailable(): boolean {
    return this.code === 'quiz_unavailable';
  }
}

export function createApiClient(options: ApiClientOptions = {}) {
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '');
  const doFetch = options.fetch ?? globalThis.fetch.bind(globalThis);

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    const response = await doFetch(`${baseUrl}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(init?.headers ?? {}),
      },
    });

    if (!response.ok) {
      throw await toApiError(response);
    }
    return (await response.json()) as T;
  }

  async function toApiError(response: Response): Promise<ApiError> {
    let code = 'unknown_error';
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = (await response.json()) as { error?: string; message?: string };
      code = body.error ?? code;
      message = body.message ?? message;
    } catch {
      // resposta sem corpo JSON — a mensagem do status já serve
    }
    return new ApiError(response.status, code, message);
  }

  return {
    /** Árvore completa, com bloqueio já resolvido para o usuário. */
    getTree: () => request<TreeView>('/api/curriculum/tree'),

    getNode: (code: string) => request<NodeDetail>(`/api/nodes/${encodeURIComponent(code)}`),

    submitQuiz: (code: string, submission: QuizSubmission) =>
      request<QuizResult>(`/api/nodes/${encodeURIComponent(code)}/quiz`, {
        method: 'POST',
        body: JSON.stringify(submission),
      }),

    logDrill: (code: string, drill: DrillRequest) =>
      request<DrillResult>(`/api/nodes/${encodeURIComponent(code)}/drill`, {
        method: 'POST',
        body: JSON.stringify(drill),
      }),

    /**
     * Grava a anotação fixada do nó. Nota em branco limpa — o backend normaliza, e o cliente não
     * duplica essa regra.
     */
    savePinnedNote: (code: string, note: string) =>
      request<PinnedNote>(`/api/nodes/${encodeURIComponent(code)}/note`, {
        method: 'PUT',
        body: JSON.stringify({ note }),
      }),

    getStreak: () => request<StreakView>('/api/streak'),

    /** Critérios de sucesso do MVP medidos sobre o uso real (docs/05-mvp-web-plano.md). */
    getMvpMetrics: (days?: number) =>
      request<MvpMetrics>(`/api/metrics/mvp${days ? `?days=${days}` : ''}`),

    /** O que drillar hoje, do mais atrasado para o menos. */
    getReviewsToday: () => request<ReviewAgenda>('/api/reviews/today'),

    getDisclaimer: () => request<DisclaimerStatus>('/api/disclaimer'),

    acceptDisclaimer: (version: string) =>
      request<DisclaimerStatus>('/api/disclaimer/accept', {
        method: 'POST',
        body: JSON.stringify({ version }),
      }),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
