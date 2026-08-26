/**
 * Cliente da API do FightOssStreak.
 *
 * Fica em `shared/` porque é reaproveitado integralmente na migração para React Native — só a
 * camada de UI é reescrita (docs/03-estrutura-projeto.md).
 */
import type {
  AccountView,
  AuthProviders,
  DemoSession,
  DisclaimerStatus,
  DrillRequest,
  DrillResult,
  FeedbackList,
  FeedbackRequest,
  FeedbackStatus,
  FeedbackView,
  MvpMetrics,
  NodeDetail,
  PinnedNote,
  QuizResult,
  QuizSubmission,
  LinkStatus,
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

  /**
   * A rotação de banco de perguntas (issue #59, D44) avançou entre a tela carregar e a resposta
   * chegar — outra aba, ou uma submissão antiga reenviada. Recarregar o nó resolve.
   */
  get isQuizStale(): boolean {
    return this.code === 'quiz_stale';
  }

  /** Sem sessão: a resposta é a tela de login, não uma mensagem de erro. */
  get isUnauthenticated(): boolean {
    return this.status === 401;
  }

  /** Não há demonstração configurada neste ambiente — o botão nem devia estar na tela. */
  get isDemoUnavailable(): boolean {
    return this.code === 'demo_indisponivel';
  }

  /** Teto de demonstrações vivas, ou freio por IP: passa, e a tela pode oferecer tentar de novo. */
  get isDemoBusy(): boolean {
    return this.code === 'demo_lotado';
  }

  /** Conta de demonstração (D39) tentando mandar feedback — ela não tem identidade nem prazo. */
  get isFeedbackNotAllowed(): boolean {
    return this.code === 'feedback_nao_permitido';
  }

  /**
   * E-mail inexistente ou senha errada, sem dizer qual dos dois (D47).
   *
   * A indistinção é do backend, e a tela a repete: separar transformaria o login em consulta de
   * quem tem conta no app.
   */
  get isBadCredentials(): boolean {
    return this.code === 'credencial_invalida';
  }

  /** Senha certa, e-mail ainda não confirmado. A tela oferece reenviar o link. */
  get isEmailUnverified(): boolean {
    return this.code === 'email_nao_verificado';
  }

  /** Freio de tentativas: por e-mail e por IP. Passa sozinho — a tela pede para esperar. */
  get isTooManyAttempts(): boolean {
    return this.code === 'muitas_tentativas';
  }

  /**
   * Ambiente sem provedor de envio de e-mail: não há cadastro por senha aqui.
   *
   * É o caso de dev e do CI, onde a aplicação sobe sem segredo nenhum. A tela precisa dizer isso
   * em vez de mostrar um formulário que sempre falha.
   */
  get isSignUpUnavailable(): boolean {
    return this.code === 'cadastro_indisponivel';
  }

  /**
   * Sessão válida, conta bloqueada.
   *
   * É 403 e não 401 de propósito: 401 devolveria a pessoa para o login que ela acabou de fazer.
   * Nada no app produz este estado desde a D48 — ver `AccessStatus` no backend para por que ele
   * continua de pé.
   */
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

    /** Conta autenticada: quem é, e se administra o app (D48). */
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

    /**
     * Cria a conta e dispara o link de confirmação (D47).
     *
     * Não abre sessão: a conta nasce não verificada, e quem autentica é o link. A resposta é
     * idêntica para e-mail novo e já cadastrado — de propósito, para não virar consulta de quem
     * tem conta no app. A tela diz "enviamos para este endereço", e é a verdade nos dois casos.
     */
    signUp: (email: string, senha: string, nome?: string) =>
      requestNoContent('/api/auth/cadastro', {
        method: 'POST',
        body: JSON.stringify({ email, senha, nome }),
      }),

    /** Entra com e-mail e senha. Abre sessão — daí não haver corpo de resposta. */
    signInWithPassword: (email: string, senha: string) =>
      requestNoContent('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, senha }),
      }),

    /**
     * O link de confirmação ainda vale?
     *
     * Consulta que não gasta o link, exatamente como a da redefinição — e pelo mesmo motivo, que
     * aqui é ainda mais concreto: quem abre a URL de um e-mail nem sempre é a pessoa. Varredor de
     * link corporativo e antivírus de caixa de entrada seguem tudo que chega, e confirmar no GET
     * queimaria o link antes do clique.
     */
    checkVerificationLink: (token: string) =>
      request<LinkStatus>(`/api/auth/verificar/${encodeURIComponent(token)}`),

    /** Confirma o e-mail e abre a sessão. É o clique da pessoa, não a abertura da URL. */
    confirmEmail: (token: string) =>
      requestNoContent(`/api/auth/verificar/${encodeURIComponent(token)}`, { method: 'POST' }),

    /** Outro link de confirmação. Responde igual para cadastro pendente, confirmado e inexistente. */
    resendVerification: (email: string) =>
      requestNoContent('/api/auth/verificacao/reenviar', {
        method: 'POST',
        body: JSON.stringify({ email }),
      }),

    /** Link de redefinição. Mesma resposta para endereço com conta e sem conta. */
    requestPasswordReset: (email: string) =>
      requestNoContent('/api/auth/senha/esquecida', {
        method: 'POST',
        body: JSON.stringify({ email }),
      }),

    /**
     * O link de redefinição ainda vale?
     *
     * Consulta que não gasta o link — consumir na abertura da tela o queimaria em qualquer
     * pré-carregamento do navegador ou do cliente de e-mail.
     */
    checkPasswordResetLink: (token: string) =>
      request<LinkStatus>(`/api/auth/senha/redefinir/${encodeURIComponent(token)}`),

    /**
     * Troca a senha.
     *
     * Não abre sessão de propósito: a troca derruba as sessões abertas da conta, e abrir uma aqui
     * criaria a única que ela não derruba. A próxima tela é o login, com a senha nova.
     */
    resetPassword: (token: string, senha: string) =>
      requestNoContent(`/api/auth/senha/redefinir/${encodeURIComponent(token)}`, {
        method: 'POST',
        body: JSON.stringify({ senha }),
      }),

    /**
     * Abre uma demonstração: conta descartável, cópia da conta-modelo, sessão de verdade (#62).
     *
     * Público, como as duas rotas de e-mail. O destino vem na resposta em vez de fixo aqui: quem
     * sabe o que a sessão recém-aberta já pode ver é quem a abriu.
     */
    startDemo: () => request<DemoSession>('/api/demo/sessao', { method: 'POST' }),

    /**
     * Envia um feedback: bug, conteúdo errado, troca de vídeo, sugestão (docs/13-feedback-usuarios.md).
     * `nodeCode` é opcional — nem todo feedback é sobre um nó do currículo.
     */
    submitFeedback: (feedback: FeedbackRequest) =>
      request<FeedbackView>('/api/feedback', {
        method: 'POST',
        body: JSON.stringify(feedback),
      }),

    /** Fila de feedback. Só a conta de administração (D48) recebe 200 aqui. */
    getFeedbackQueue: () => request<FeedbackList>('/api/admin/feedback'),

    decideFeedback: (id: number, status: FeedbackStatus) =>
      request<FeedbackView>(`/api/admin/feedback/${id}/status`, {
        method: 'POST',
        body: JSON.stringify({ status }),
      }),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
