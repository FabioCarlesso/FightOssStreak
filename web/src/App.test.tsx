import type { AccountView, DisclaimerStatus, ReviewAgenda, StreakView, TreeView } from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
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

  apiMock.getAccount.mockResolvedValue({
    displayName: 'Autor',
    email: 'autor@example.test',
    provider: 'google',
    accessStatus: 'APROVADO',
    owner: false,
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
  it('a landing abre sem aceite e sem tocar na API', async () => {
    renderEm('/');

    expect(await screen.findByRole('heading', { level: 1, name: MANCHETE })).toBeInTheDocument();
    // A promessa que faz a página existir: com o backend frio, ela aparece do mesmo jeito. Vale
    // também para o portão de autenticação, que entrou depois dela (#24).
    expect(apiMock.getDisclaimer).not.toHaveBeenCalled();
    expect(apiMock.getAccount).not.toHaveBeenCalled();
  });

  it('o botão de entrar aponta para o app, não para dentro do conteúdo', async () => {
    renderEm('/');

    const entrar = await screen.findAllByRole('link', { name: /pedir acesso/i });
    expect(entrar[0]).toHaveAttribute('href', '/hoje');
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
