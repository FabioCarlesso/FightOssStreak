import type {
  AccountView,
  AuthProviders,
  DemoSession,
  DisclaimerStatus,
  ReviewAgenda,
  StreakView,
  TreeView,
} from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App.tsx';

/**
 * A fronteira entre a landing e o app.
 *
 * Extensão consciente da D29, pelo mesmo critério da D31: o que se testa aqui não é layout, é o
 * risco de a página pública virar caminho para dentro do app sem o aceite do aviso — o defeito mais
 * grave que este projeto pode ter (docs/06). Junto vem a outra promessa estrutural da landing, que
 * é **não** falar com a API: era o portão que decidia a raiz, e ele depende de `GET /api/disclaimer`
 * para renderizar, então com o backend frio a primeira tela era um erro de rede.
 *
 * O cliente de API é mockado no módulo: nenhum teste toca a rede.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getAccount: vi.fn<() => Promise<AccountView>>(),
    getDisclaimer: vi.fn<() => Promise<DisclaimerStatus>>(),
    acceptDisclaimer: vi.fn<(version: string) => Promise<DisclaimerStatus>>(),
    getStreak: vi.fn<() => Promise<StreakView>>(),
    getReviewsToday: vi.fn<() => Promise<ReviewAgenda>>(),
    getTree: vi.fn<() => Promise<TreeView>>(),
    getAuthProviders: vi.fn<() => Promise<AuthProviders>>(),
    startDemo: vi.fn<() => Promise<DemoSession>>(),
  },
}));

vi.mock('./api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const MANCHETE = /na sexta, lembra de uma/i;

function renderEm(rota: string) {
  return render(
    <MemoryRouter initialEntries={[rota]}>
      <App />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  sessionStorage.clear();

  apiMock.getAuthProviders.mockResolvedValue({
    providers: [],
    demoEnabled: false,
  });
  apiMock.startDemo.mockResolvedValue({ destino: '/hoje', expiraEm: '2026-08-18T12:00:00Z' });

  apiMock.getAccount.mockResolvedValue({
    displayName: 'Autor',
    email: 'autor@example.test',
    provider: 'google',
    accessStatus: 'APROVADO',
    role: 'USUARIO',
  });
  apiMock.getDisclaimer.mockResolvedValue({ accepted: false, currentVersion: '2026-08' });
  apiMock.getStreak.mockResolvedValue({
    currentStreak: 3,
    longestStreak: 5,
    drilledToday: true,
    activeDaysLast30: 9,
    targetDaysLast30: 12,
    today: '2026-08-18',
  });
  apiMock.getReviewsToday.mockResolvedValue({ today: '2026-08-18', dueCount: 0, due: [] });
  apiMock.getTree.mockResolvedValue({
    modules: [],
    summary: { totalNodes: 46, completedNodes: 0, availableNodes: 1, lockedNodes: 45 },
  });
});

describe('rota pública', () => {
  it('a landing abre sem aceite e sem depender da API', async () => {
    renderEm('/');

    expect(await screen.findByRole('heading', { level: 1, name: MANCHETE })).toBeInTheDocument();
    // A promessa que faz a página existir: com o backend frio, ela aparece do mesmo jeito. Vale
    // também para o portão de autenticação, que entrou depois dela (#24).
    expect(apiMock.getDisclaimer).not.toHaveBeenCalled();
    expect(apiMock.getAccount).not.toHaveBeenCalled();
  });

  it('com a API fora do ar, a página aparece inteira — só sem o botão de demonstração', async () => {
    // O caso de cold start, que é o comum para quem recebe o link. A #62 acrescentou uma pergunta
    // à API; se a resposta dela virasse requisito, a landing voltaria a quebrar com backend frio.
    apiMock.getAuthProviders.mockRejectedValue(new Error('backend frio'));

    renderEm('/');

    expect(await screen.findByRole('heading', { level: 1, name: MANCHETE })).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /ver o app funcionando/i }),
    ).not.toBeInTheDocument();
  });

  it('o botão de entrada leva ao login, não para dentro do conteúdo', async () => {
    renderEm('/');

    // `/entrar` e não `/hoje`: quem chega pela apresentação ainda não tem sessão, e mandá-lo para
    // dentro seria um portão sem explicação. E `/entrar` e não `/cadastrar`: só a tela de entrada
    // oferece as duas saídas — provedor externo e criar conta com senha —, e quem escolhe é a
    // pessoa.
    const entrar = await screen.findAllByRole('link', { name: /entrar ou criar conta/i });
    expect(entrar[0]).toHaveAttribute('href', '/entrar');
  });
});

describe('acesso demonstrativo (#62)', () => {
  it('sem conta-modelo configurada, o botão não aparece', async () => {
    renderEm('/');

    await screen.findByRole('heading', { level: 1, name: MANCHETE });
    expect(
      screen.queryByRole('button', { name: /ver o app funcionando/i }),
    ).not.toBeInTheDocument();
  });

  it('com conta-modelo, o botão abre a sessão e entra no app', async () => {
    apiMock.getAuthProviders.mockResolvedValue({
      providers: [],
      demoEnabled: true,
    });

    renderEm('/');
    const botao = await screen.findByRole('button', { name: /ver o app funcionando/i });
    await userEvent.click(botao);

    expect(apiMock.startDemo).toHaveBeenCalledTimes(1);
    // O destino vem do servidor, e leva para dentro do app — que continua atrás do aviso, como
    // para qualquer um: o aceite do disclaimer não é copiado da conta-modelo.
    expect(await screen.findByRole('button', { name: /li e concordo/i })).toBeInTheDocument();
  });

  it('a demonstração não pula o aviso de responsabilidade', async () => {
    apiMock.getAuthProviders.mockResolvedValue({
      providers: [],
      demoEnabled: true,
    });
    apiMock.getAccount.mockResolvedValue({
      displayName: 'Visitante',
      email: undefined,
      provider: 'demo',
      accessStatus: 'APROVADO',
      role: 'USUARIO',
      demoExpiresAt: '2026-08-18T12:00:00Z',
    });

    renderEm('/');
    await userEvent.click(await screen.findByRole('button', { name: /ver o app funcionando/i }));

    expect(await screen.findByRole('button', { name: /li e concordo/i })).toBeInTheDocument();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
  });

  it('dentro do app, a faixa diz que a conta é temporária e some sozinha', async () => {
    apiMock.getDisclaimer.mockResolvedValue({
      accepted: true,
      currentVersion: '2026-08',
      acceptedVersion: '2026-08',
    });
    apiMock.getAccount.mockResolvedValue({
      displayName: 'Visitante',
      email: undefined,
      provider: 'demo',
      accessStatus: 'APROVADO',
      role: 'USUARIO',
      demoExpiresAt: '2026-08-18T12:00:00Z',
    });

    renderEm('/hoje');

    expect(await screen.findByText(/você está numa demonstração/i)).toBeInTheDocument();
    // A outra faixa de "demonstração" (D31) fala o contrário — que nada é gravado. Elas não podem
    // se confundir, e é por isso que este teste procura o texto, não a classe.
    expect(screen.queryByText(/nada é gravado no progresso/i)).not.toBeInTheDocument();
  });

  it('quem só viu a demonstração continua achando a landing na raiz', async () => {
    apiMock.getDisclaimer.mockResolvedValue({
      accepted: true,
      currentVersion: '2026-08',
      acceptedVersion: '2026-08',
    });
    apiMock.getAccount.mockResolvedValue({
      displayName: 'Visitante',
      email: undefined,
      provider: 'demo',
      accessStatus: 'APROVADO',
      role: 'USUARIO',
      demoExpiresAt: '2026-08-18T12:00:00Z',
    });

    const { unmount } = renderEm('/hoje');
    await screen.findByText(/você está numa demonstração/i);
    unmount();

    renderEm('/');

    // A apresentação é o que existe para convencer alguém a criar conta. Escondê-la de quem
    // acabou de experimentar — e que por definição ainda não tem conta — inverte a intenção.
    expect(await screen.findByRole('heading', { level: 1, name: MANCHETE })).toBeInTheDocument();
  });

  it('conta comum não vê faixa nenhuma', async () => {
    apiMock.getDisclaimer.mockResolvedValue({
      accepted: true,
      currentVersion: '2026-08',
      acceptedVersion: '2026-08',
    });

    renderEm('/hoje');

    await screen.findByRole('heading', { level: 2, name: /revise hoje/i });
    expect(screen.queryByText(/você está numa demonstração/i)).not.toBeInTheDocument();
  });
});

describe('portão de aceite', () => {
  it.each(['/hoje', '/arvore', '/no/M0.1', '/progresso', '/rota-que-nao-existe'])(
    'sem aceite, %s continua exigindo o aviso',
    async (rota) => {
      renderEm(rota);

      expect(await screen.findByRole('button', { name: /li e concordo/i })).toBeInTheDocument();
      expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    },
  );

  it('quem parou no aviso não conta como quem entrou: a raiz segue mostrando a landing', async () => {
    const { unmount } = renderEm('/hoje');
    await screen.findByRole('button', { name: /li e concordo/i });
    unmount();

    renderEm('/');
    expect(await screen.findByRole('heading', { level: 1, name: MANCHETE })).toBeInTheDocument();
  });
});

describe('visitante que já entrou', () => {
  async function entrarNoApp() {
    apiMock.getDisclaimer.mockResolvedValue({
      accepted: true,
      currentVersion: '2026-08',
      acceptedVersion: '2026-08',
    });
    const { unmount } = renderEm('/hoje');
    await screen.findByRole('heading', { level: 2, name: /revise hoje/i });
    unmount();
  }

  it('a raiz vira atalho para a agenda do dia', async () => {
    await entrarNoApp();

    renderEm('/');

    await waitFor(() => {
      expect(screen.getByRole('heading', { level: 2, name: /revise hoje/i })).toBeInTheDocument();
    });
    expect(screen.queryByRole('heading', { level: 1, name: MANCHETE })).not.toBeInTheDocument();
  });

  it('mas `?ver` traz a apresentação de volta, para o link continuar compartilhável', async () => {
    await entrarNoApp();

    renderEm('/?ver=apresentacao');

    expect(await screen.findByRole('heading', { level: 1, name: MANCHETE })).toBeInTheDocument();
  });
});
