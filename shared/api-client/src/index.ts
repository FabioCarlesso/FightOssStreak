/**
 * Cliente da API do FightOssStreak.
 *
 * Fica em `shared/` porque é reaproveitado integralmente na migração para React Native — só a
 * camada de UI é reescrita (docs/03-estrutura-projeto.md).
 */
import type {
  AccessRequests,
  AccountView,
  AuthProviders,
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

  /** Sem sessão: a resposta é a tela de login, não uma mensagem de erro. */
  get isUnauthenticated(): boolean {
    return this.status === 401;
  }

  /**
   * Autenticado, mas a conta ainda não foi liberada.
   *
   * É 403 e não 401 de propósito: 401 devolveria a pessoa para o login que ela acabou de fazer.
   */
  get isAccessPending(): boolean {
    return this.code === 'acesso_pendente';
  }

  get isAccessDenied(): boolean {
    return this.code === 'acesso_recusado';
  }
}

/**
 * Token de CSRF que o backend deixou no cookie `XSRF-TOKEN`.
 *
 * O cookie é legível por script de propósito (`withHttpOnlyFalse` no backend): é assim que o app
 * prova, em cada escrita, que a requisição partiu dele e não de outra origem com o cookie de
 * sessão a tiracolo. Fora do browser não há cookie — e nem sessão de navegador para forjar.
 */
function csrfToken(): string | null {
  if (typeof document === 'undefined') return null;
  const match = /(?:^|;\s*)XSRF-TOKEN=([^;]*)/.exec(document.cookie);
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export function createApiClient(options: ApiClientOptions = {}) {
  const baseUrl = (options.baseUrl ?? '').replace(/\/$/, '');
  const doFetch = options.fetch ?? globalThis.fetch.bind(globalThis);

  async function send(path: string, init?: RequestInit): Promise<Response> {
    const method = (init?.method ?? 'GET').toUpperCase();
    const token = SAFE_METHODS.has(method) ? null : csrfToken();
    const response = await doFetch(`${baseUrl}${path}`, {
      ...init,
      // O cookie de sessão é a autenticação. Explícito porque o default varia entre
      // implementações de fetch, e sem ele toda requisição voltaria 401.
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'X-XSRF-TOKEN': token } : {}),
        ...(init?.headers ?? {}),
      },
    });

    if (!response.ok) {
      throw await toApiError(response);
    }
    return response;
  }

  async function request<T>(path: string, init?: RequestInit): Promise<T> {
    return (await (await send(path, init)).json()) as T;
  }

  /** Para 204 sem corpo: `response.json()` num corpo vazio estoura. */
  async function requestNoContent(path: string, init?: RequestInit): Promise<void> {
    await send(path, init);
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

    /**
     * Provedores de login habilitados. Público: é a única chamada que funciona sem sessão.
     */
    getAuthProviders: () => request<AuthProviders>('/api/auth/providers'),

    /** Conta autenticada e estado do acesso. Responde também para conta pendente ou recusada. */
    getAccount: () => request<AccountView>('/api/me'),

    /**
     * Encerra a sessão.
     *
     * Não sai do spec OpenAPI porque quem responde é o filtro de logout do Spring Security, e
     * não um controller — daí o caminho literal aqui.
     */
    logout: () => requestNoContent('/api/logout', { method: 'POST' }),

    /** Exclusão irreversível da conta e de todo o dado dela. */
    deleteAccount: () => requestNoContent('/api/me', { method: 'DELETE' }),

    /** Fila de solicitações de acesso. Só o dono do app recebe 200 aqui. */
    getAccessRequests: () => request<AccessRequests>('/api/admin/solicitacoes'),

    approveAccessRequest: (id: number) =>
      request<AccessRequests>(`/api/admin/solicitacoes/${id}/aprovar`, { method: 'POST' }),

    denyAccessRequest: (id: number) =>
      request<AccessRequests>(`/api/admin/solicitacoes/${id}/recusar`, { method: 'POST' }),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
