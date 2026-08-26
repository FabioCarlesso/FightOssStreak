import type { AccountView, AuthProviders, DemoSession } from '@fos/types';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { notifyAccessDenied } from '../state/accessDenied.ts';
import { AuthGate } from './AuthGate.tsx';

/**
 * O portão de autenticação decide quem vê o app.
 *
 * Desde a D48 ele decide **uma** coisa: há sessão, ou não há. Os estados intermediários que a D36
 * criou — pendente e recusado — saíram com a fila de aprovação, e com eles as duas telas que este
 * arquivo testava. O que sobra aqui é a fronteira que ainda existe: sem sessão vai para `/entrar`,
 * com sessão abre o app, e demonstração vencida tem tela própria.
 *
 * O cliente de API é mockado no módulo: nenhum teste toca a rede.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getAccount: vi.fn<() => Promise<AccountView>>(),
    getAuthProviders: vi.fn<() => Promise<AuthProviders>>(),
    logout: vi.fn<() => Promise<void>>(),
    deleteAccount: vi.fn<() => Promise<void>>(),
    startDemo: vi.fn<() => Promise<DemoSession>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const { ApiError } = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');

const APP = 'árvore do currículo';
const ENTRAR = 'a tela de entrada';

/**
 * O portão redireciona em vez de renderizar o login, então o teste precisa de um roteador com a
 * rota de destino — é ela que prova que quem não tem sessão sai daqui.
 */
function renderGate() {
  return render(
    <MemoryRouter initialEntries={['/hoje']}>
      <Routes>
        <Route
          path="/hoje"
          element={
            <AuthGate>
              <p>{APP}</p>
            </AuthGate>
          }
        />
        <Route path="/entrar" element={<p>{ENTRAR}</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

function conta(overrides: Partial<AccountView> = {}): AccountView {
  return {
    displayName: 'Ana',
    email: 'ana@example.test',
    provider: 'password',
    accessStatus: 'APROVADO',
    role: 'USUARIO',
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  apiMock.getAuthProviders.mockResolvedValue({
    providers: [
      { id: 'google', label: 'Google', authorizationUrl: '/api/oauth2/authorization/google' },
    ],
    demoEnabled: false,
    passwordEnabled: true,
  });
  apiMock.startDemo.mockResolvedValue({ destino: '/hoje', expiraEm: '2026-08-18T12:00:00Z' });
  apiMock.logout.mockResolvedValue(undefined);
  apiMock.deleteAccount.mockResolvedValue(undefined);
});

describe('AuthGate', () => {
  it('sem sessão, manda para a tela de entrada', async () => {
    apiMock.getAccount.mockRejectedValue(
      new ApiError(401, 'nao_autenticado', 'Requisição sem sessão autenticada.'),
    );

    renderGate();

    expect(await screen.findByText(ENTRAR)).toBeInTheDocument();
    expect(screen.queryByText(APP)).not.toBeInTheDocument();
  });

  it('com sessão, abre o app', async () => {
    apiMock.getAccount.mockResolvedValue(conta());

    renderGate();

    expect(await screen.findByText(APP)).toBeInTheDocument();
  });

  it('API fora do ar não vira tela de entrada: o erro é dito, com nova tentativa', async () => {
    apiMock.getAccount.mockRejectedValueOnce(new Error('Failed to fetch'));

    renderGate();

    // Confundir "backend frio" com "não está logado" mandaria a pessoa fazer um login que não
    // resolveria nada, e esconderia a causa real.
    expect(await screen.findByText(/não foi possível falar com a API/i)).toBeInTheDocument();
    expect(screen.queryByText(ENTRAR)).not.toBeInTheDocument();

    apiMock.getAccount.mockResolvedValue(conta());
    await userEvent.click(screen.getByRole('button', { name: /tentar de novo/i }));

    expect(await screen.findByText(APP)).toBeInTheDocument();
  });
});

/**
 * Conta bloqueada (#90, #91).
 *
 * O caminho normal é o 200: `/api/me` fica fora do portão do backend, e a conta bloqueada continua
 * sabendo quem é — o que muda é o `accessStatus`. O 403 das outras chamadas é o caminho de reforço,
 * e o que não pode acontecer em nenhum dos dois é virar redirecionamento para o login: a pessoa
 * acabou de entrar, e o login não desbloqueia nada.
 */
describe('AuthGate com conta bloqueada', () => {
  it('sessão viva com acesso recusado cai na tela de bloqueio, não no login', async () => {
    apiMock.getAccount.mockResolvedValue(conta({ accessStatus: 'RECUSADO' }));

    renderGate();

    expect(
      await screen.findByRole('heading', { name: /acesso está bloqueado/i }),
    ).toBeInTheDocument();
    expect(screen.queryByText(ENTRAR)).not.toBeInTheDocument();
    expect(screen.queryByText(APP)).not.toBeInTheDocument();
  });

  it('403 de acesso recusado também não vira looping de login', async () => {
    apiMock.getAccount.mockRejectedValue(
      new ApiError(403, 'acesso_recusado', 'Acesso bloqueado por quem administra o app.'),
    );

    renderGate();

    expect(
      await screen.findByRole('heading', { name: /acesso está bloqueado/i }),
    ).toBeInTheDocument();
    expect(screen.queryByText(ENTRAR)).not.toBeInTheDocument();
  });

  it('bloqueio no meio do uso derruba a aba aberta na tela certa', async () => {
    apiMock.getAccount.mockResolvedValue(conta());

    renderGate();
    await screen.findByText(APP);

    // O que o servidor faz é derrubar a sessão; a aba só descobre quando a chamada seguinte —
    // de qualquer tela — volta 403. Recarregar a página não é instrução que se dê a ninguém.
    apiMock.getAccount.mockResolvedValue(conta({ accessStatus: 'RECUSADO' }));
    act(() => notifyAccessDenied());

    expect(
      await screen.findByRole('heading', { name: /acesso está bloqueado/i }),
    ).toBeInTheDocument();
    expect(screen.queryByText(ENTRAR)).not.toBeInTheDocument();
  });

  it('quem está bloqueado ainda consegue excluir a própria conta', async () => {
    apiMock.getAccount.mockResolvedValue(conta({ accessStatus: 'RECUSADO' }));

    renderGate();
    await screen.findByRole('heading', { name: /acesso está bloqueado/i });

    // Bloquear não pode virar sequestro de dado pessoal: `DELETE /api/me` fica de fora do portão
    // do backend de propósito, e a tela precisa oferecer a saída.
    await userEvent.click(screen.getByRole('button', { name: /excluir minha conta/i }));
    await userEvent.click(screen.getByRole('button', { name: /sim, excluir tudo/i }));

    expect(apiMock.deleteAccount).toHaveBeenCalledTimes(1);
  });

  it('sair da sessão continua sendo a outra saída', async () => {
    apiMock.getAccount.mockResolvedValue(conta({ accessStatus: 'RECUSADO' }));

    renderGate();
    await screen.findByRole('heading', { name: /acesso está bloqueado/i });

    await userEvent.click(screen.getByRole('button', { name: 'Sair' }));

    expect(apiMock.logout).toHaveBeenCalledTimes(1);
  });
});

/**
 * Demonstração vencida (#62).
 *
 * O servidor responde 401, igual a quem nunca entrou — e é a resposta certa: passado o prazo a
 * conta não existe mais para quem está do outro lado. Quem sabe que havia uma demonstração é o
 * navegador, e é isso que separa "crie uma conta" de "a demonstração acabou".
 */
describe('AuthGate com demonstração vencida', () => {
  const MARCA = 'fos.demo-conta';

  it('sem marca de demonstração, 401 continua mandando para a entrada', async () => {
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));

    renderGate();

    expect(await screen.findByText(ENTRAR)).toBeInTheDocument();
  });

  it('com marca, a tela explica o que aconteceu em vez de mandar entrar', async () => {
    sessionStorage.setItem(MARCA, 'sim');
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));

    renderGate();

    expect(
      await screen.findByRole('heading', { name: /a demonstração terminou/i }),
    ).toBeInTheDocument();
    expect(screen.queryByText(ENTRAR)).not.toBeInTheDocument();
  });

  it('recomeçar abre outra demonstração e volta para o app', async () => {
    sessionStorage.setItem(MARCA, 'sim');
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));

    renderGate();
    await screen.findByRole('heading', { name: /a demonstração terminou/i });

    apiMock.getAccount.mockResolvedValue(
      conta({ provider: 'demo', email: undefined, demoExpiresAt: '2026-08-18T12:00:00Z' }),
    );
    await userEvent.click(screen.getByRole('button', { name: /começar outra demonstração/i }));

    expect(apiMock.startDemo).toHaveBeenCalledTimes(1);
    expect(await screen.findByText(APP)).toBeInTheDocument();
  });

  it('quem quer conta de verdade limpa a marca e cai na entrada', async () => {
    sessionStorage.setItem(MARCA, 'sim');
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));

    renderGate();
    await screen.findByRole('heading', { name: /a demonstração terminou/i });

    await userEvent.click(screen.getByRole('button', { name: /criar conta ou entrar/i }));

    // Sem limpar a marca, o 401 seguinte cairia de novo nesta mesma tela — e a pessoa ficaria
    // presa num loop de demonstração sem nunca ver a entrada.
    expect(await screen.findByText(ENTRAR)).toBeInTheDocument();
    expect(sessionStorage.getItem(MARCA)).toBeNull();
  });
});
