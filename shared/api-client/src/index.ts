/**
 * Cliente da API do FightOssStreak.
 *
 * Fica em `shared/` porque é reaproveitado integralmente na migração para React Native — só a
 * camada de UI é reescrita (docs/03-estrutura-projeto.md).
 */
import type {
  AccessStatus,
  AccountView,
  AdminUserPage,
  AdminUserView,
  AuthProviders,
  DemoSession,
  DisclaimerStatus,
  DrillRequest,
  DrillResult,
  FeedbackList,
  FeedbackRequest,
  FeedbackStatus,
  FeedbackView,
  HealthView,
  MvpMetrics,
  NodeDetail,
  PanelView,
  Role,
  PinnedNote,
  QuizResult,
  QuizSubmission,
  LinkStatus,
  ReviewAgenda,
  StreakView,
  TreeView,
  UsageEventRequest,
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
   * Ambiente com a coleta de uso desligada (`fos.usage.enabled=false`).
   *
   * É o código, e não o 503, que importa: 503 sem código é o que o proxy devolve enquanto o
   * backend reinicia, e aquele não pode desligar a coleta do resto da visita.
   */
  get isUsageDisabled(): boolean {
    return this.code === 'coleta_desligada';
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

  /**
   * Ação de administração que conflita com o estado atual (#89, #90).
   *
   * São quatro guardas com códigos próprios — e-mail não confirmado, ação sobre a própria conta,
   * última conta de administração, conta de demonstração —, porque cada uma leva quem está do
   * outro lado a uma decisão diferente. A tela traduz o código; ela não recria a regra.
   */
  get isAdminConflict(): boolean {
    return this.code.startsWith('admin_');
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

/**
 * Os três recortes do painel (#85).
 *
 * União de literais, e não `number`: o backend aceita exatamente estes três, e um tipo mais largo
 * empurraria para a tela a chance de pedir um período que sempre volta `400`.
 */
export type PanelDays = 7 | 30 | 90;

/**
 * Os três recortes da seção de saúde (#86), em horas.
 *
 * Horas e não dias, ao contrário do painel de uso: a leitura aqui é operacional — o incidente que
 * interessa é o de agora, e um recorte de 7 dias como menor unidade o esconderia dentro da média.
 */
export type HealthHours = 24 | 72 | 168;

/** Filtros da listagem de contas (#89). Todos opcionais e combináveis entre si. */
export interface AdminUsersQuery {
  readonly status?: AccessStatus;
  readonly role?: Role;
  readonly verificado?: boolean;
  readonly busca?: string;
  readonly page?: number;
  /** Teto de 100 no backend; pedir mais devolve o teto, não um erro. */
  readonly size?: number;
}

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

  /**
   * O servidor já disse que a coleta está desligada aqui.
   *
   * Por cliente e em memória: some com o recarregar da página, que é o momento certo de perguntar
   * de novo — é quando um deploy que religou a coleta passaria a valer.
   */
  let usoDesligado = false;

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
     * Registra um acesso a uma rota do app (#84, D50).
     *
     * **Nunca rejeita, e é por isso que devolve `void`.** A landing (D33) não pode passar a
     * depender da coleta para renderizar: evento que falha é evento perdido, nunca tela quebrada.
     * Quem chama não tem o que fazer com o erro, então ele não sobe.
     *
     * O que vai no corpo é só o que o navegador sabe — em que rota está, de que site veio, com que
     * campanha. Dispositivo, navegador, sistema, idioma e país o servidor deriva da requisição, e
     * um corpo que trouxesse esses campos seria ignorado.
     */
    recordUsage: async (event: UsageEventRequest): Promise<void> => {
      // Ambiente com a coleta desligada já se anunciou: não insista. Sem esta guarda,
      // `FOS_USAGE_ENABLED=false` significaria só "não grava" — o navegador seguiria mandando uma
      // requisição por navegação, para sempre, para um servidor que as joga fora.
      if (usoDesligado) return;
      try {
        await requestNoContent('/api/telemetria/evento', {
          method: 'POST',
          body: JSON.stringify(event),
        });
      } catch (erro) {
        if (erro instanceof ApiError && erro.isUsageDisabled) {
          usoDesligado = true;
          return;
        }
        // Offline, backend frio, 429 do freio: nada disso é problema de quem navega, e nada disso
        // desliga a coleta — só o código explícito desliga.
      }
    },

    /**
     * Envia um feedback: bug, conteúdo errado, troca de vídeo, sugestão (docs/13-feedback-usuarios.md).
     * `nodeCode` é opcional — nem todo feedback é sobre um nó do currículo.
     */
    submitFeedback: (feedback: FeedbackRequest) =>
      request<FeedbackView>('/api/feedback', {
        method: 'POST',
        body: JSON.stringify(feedback),
      }),

    /**
     * Painel de uso do app (#85). Só quem administra recebe 200.
     *
     * `dias` é um dos três presets — 7, 30 ou 90. Qualquer outro valor é `400` no backend, de
     * propósito: período livre está fora de escopo, e aceitar aqui o que o servidor recusa só
     * moveria o erro para mais longe de quem o causou.
     */
    getAdminPanel: (dias: PanelDays = 7) => request<PanelView>(`/api/admin/painel?dias=${dias}`),

    /**
     * Saúde do site (#86). Só quem administra recebe 200.
     *
     * Mesma regra do painel: os presets são os três que o backend aceita, e qualquer outro valor é
     * `400` lá — aceitar aqui o que o servidor recusa só afastaria o erro de quem o causou.
     */
    getAdminHealth: (horas: HealthHours = 24) =>
      request<HealthView>(`/api/admin/saude?horas=${horas}`),

    /** Fila de feedback. Só a conta de administração (D48) recebe 200 aqui. */
    getFeedbackQueue: () => request<FeedbackList>('/api/admin/feedback'),

    decideFeedback: (id: number, status: FeedbackStatus) =>
      request<FeedbackView>(`/api/admin/feedback/${id}/status`, {
        method: 'POST',
        body: JSON.stringify({ status }),
      }),

    /**
     * Contas do sistema, paginadas (#89). Só quem administra recebe 200.
     *
     * Filtro que não veio não entra na URL: o backend trata ausente e vazio de formas diferentes,
     * e `?status=` seria um valor de enum inválido em vez de "sem filtro".
     */
    getAdminUsers: (query: AdminUsersQuery = {}) => {
      const params = new URLSearchParams();
      if (query.status) params.set('status', query.status);
      if (query.role) params.set('role', query.role);
      if (query.verificado != null) params.set('verificado', String(query.verificado));
      if (query.busca?.trim()) params.set('busca', query.busca.trim());
      if (query.page != null) params.set('page', String(query.page));
      if (query.size != null) params.set('size', String(query.size));
      const search = params.toString();
      return request<AdminUserPage>(`/api/admin/usuarios${search ? `?${search}` : ''}`);
    },

    /** Promove a `ADMIN` ou rebaixa a `USUARIO` (#89). 409 nas guardas — ver `isAdminConflict`. */
    setAdminUserRole: (id: number, role: Role) =>
      request<AdminUserView>(`/api/admin/usuarios/${id}/role`, {
        method: 'POST',
        body: JSON.stringify({ role }),
      }),

    /**
     * Bloqueia (`RECUSADO`) ou devolve o acesso (`APROVADO`) a uma conta (#90).
     *
     * Bloquear vale na ação seguinte da conta, e não só no próximo login: o portão do backend relê
     * o estado a cada requisição, então a aba que já estava aberta cai na tela de conta bloqueada
     * sem que ninguém precise recarregar nada.
     */
    setAdminUserStatus: (id: number, status: AccessStatus, motivo?: string) =>
      request<AdminUserView>(`/api/admin/usuarios/${id}/status`, {
        method: 'POST',
        body: JSON.stringify({ status, motivo }),
      }),
  };
}

export type ApiClient = ReturnType<typeof createApiClient>;
