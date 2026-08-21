import type { AccountView, AuthProviders, DemoSession } from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthGate } from './AuthGate.tsx';

/**
 * O portão de autenticação e aprovação decide quem vê o app.
 *
 * O defeito caro aqui não é visual: é deixar entrar quem não foi liberado, ou prender quem já foi.
 * Por isso os testes olham para o que aparece na tela em cada estado da conta, e não para o
 * layout — mesma extensão consciente da D29 que o `DisclaimerGate` já tinha.
 *
 * O cliente de API é mockado no módulo: nenhum teste toca a rede.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getAccount: vi.fn<() => Promise<AccountView>>(),
    getAuthProviders: vi.fn<() => Promise<AuthProviders>>(),
    logout: vi.fn<() => Promise<void>>(),
    deleteAccount: vi.fn<() => Promise<void>>(),
    requestEmailAccess: vi.fn<(email: string) => Promise<void>>(),
    requestEmailLogin: vi.fn<(email: string) => Promise<void>>(),
    startDemo: vi.fn<() => Promise<DemoSession>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const { ApiError } = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');

const APP = 'árvore do currículo';

function renderGate() {
  return render(
    <AuthGate>
      <p>{APP}</p>
    </AuthGate>,
  );
}

function conta(overrides: Partial<AccountView> = {}): AccountView {
  return {
    displayName: 'Ana',
    email: 'ana@example.test',
    provider: 'google',
    accessStatus: 'APROVADO',
    owner: false,
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
    emailEnabled: false,
    demoEnabled: false,
  });
  apiMock.startDemo.mockResolvedValue({ destino: '/hoje', expiraEm: '2026-08-18T12:00:00Z' });
  apiMock.requestEmailAccess.mockResolvedValue(undefined);
  apiMock.requestEmailLogin.mockResolvedValue(undefined);
  apiMock.logout.mockResolvedValue(undefined);
  apiMock.deleteAccount.mockResolvedValue(undefined);
});

describe('AuthGate', () => {
  it('sem sessão, mostra a tela de login com um botão por provedor habilitado', async () => {
    apiMock.getAccount.mockRejectedValue(
      new ApiError(401, 'nao_autenticado', 'Requisição sem sessão autenticada.'),
    );

    renderGate();

    const entrar = await screen.findByRole('link', { name: /entrar com google/i });
    // Âncora, e não botão com fetch: o fluxo OAuth é navegação de página inteira.
    expect(entrar).toHaveAttribute('href', '/api/oauth2/authorization/google');
    expect(screen.queryByText(APP)).not.toBeInTheDocument();
  });

  it('sem provedor configurado, o login diz isso em vez de mostrar tela vazia', async () => {
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));
    apiMock.getAuthProviders.mockResolvedValue({ providers: [], emailEnabled: false });

    renderGate();

    expect(
      await screen.findByText(/nenhuma forma de entrada está configurada/i),
    ).toBeInTheDocument();
  });

  it('sem entrada por e-mail habilitada, não oferece a alternativa', async () => {
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));

    renderGate();

    await screen.findByRole('link', { name: /entrar com google/i });
    expect(screen.queryByRole('button', { name: /não tenho nenhuma dessas contas/i })).toBeNull();
  });

  it('quem não tem provedor pede acesso e vê o pedido registrado', async () => {
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));
    apiMock.getAuthProviders.mockResolvedValue({
      providers: [
        { id: 'google', label: 'Google', authorizationUrl: '/api/oauth2/authorization/google' },
      ],
      emailEnabled: true,
    });

    renderGate();

    await userEvent.click(
      await screen.findByRole('button', { name: /não tenho nenhuma dessas contas/i }),
    );
    await userEvent.type(screen.getByLabelText(/seu e-mail/i), 'sem-provedor@example.test');
    await userEvent.click(screen.getByRole('button', { name: /pedir acesso/i }));

    expect(apiMock.requestEmailAccess).toHaveBeenCalledWith('sem-provedor@example.test');
    expect(await screen.findByText(/pedido registrado/i)).toBeInTheDocument();
    // Não promete e-mail agora: o primeiro só sai quando o autor aprova.
    expect(screen.getByText(/quando ela sair, você recebe um link/i)).toBeInTheDocument();
  });

  it('quem já tem acesso pede o link, e a tela não revela se a conta existe', async () => {
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));
    apiMock.getAuthProviders.mockResolvedValue({ providers: [], emailEnabled: true });

    renderGate();

    await userEvent.click(
      await screen.findByRole('button', { name: /não tenho nenhuma dessas contas/i }),
    );
    await userEvent.type(screen.getByLabelText(/seu e-mail/i), 'ana@example.test');
    await userEvent.click(screen.getByRole('button', { name: /já tenho acesso/i }));

    expect(apiMock.requestEmailLogin).toHaveBeenCalledWith('ana@example.test');
    // "Se existe uma conta liberada" — a tela repete a indistinção do backend de propósito.
    expect(await screen.findByText(/se existe uma conta liberada/i)).toBeInTheDocument();
  });

  it('conta na fila vê a solicitação registrada, não o app', async () => {
    apiMock.getAccount.mockResolvedValue(conta({ accessStatus: 'PENDENTE' }));

    renderGate();

    expect(
      await screen.findByRole('heading', { name: /solicitação registrada/i }),
    ).toBeInTheDocument();
    expect(screen.getByText(/ana@example.test/i)).toBeInTheDocument();
    expect(screen.queryByText(APP)).not.toBeInTheDocument();
  });

  it('aprovada, a conta pendente entra ao verificar de novo', async () => {
    apiMock.getAccount
      .mockResolvedValueOnce(conta({ accessStatus: 'PENDENTE' }))
      .mockResolvedValue(conta());

    renderGate();
    await userEvent.click(await screen.findByRole('button', { name: /verificar de novo/i }));

    expect(await screen.findByText(APP)).toBeInTheDocument();
  });

  it('conta recusada não ganha botão de pedir de novo', async () => {
    apiMock.getAccount.mockResolvedValue(conta({ accessStatus: 'RECUSADO' }));

    renderGate();

    expect(
      await screen.findByRole('heading', { name: /acesso não liberado/i }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /pedir/i })).not.toBeInTheDocument();
    expect(screen.queryByText(APP)).not.toBeInTheDocument();
  });

  it('conta aprovada abre o app', async () => {
    apiMock.getAccount.mockResolvedValue(conta());

    renderGate();

    expect(await screen.findByText(APP)).toBeInTheDocument();
  });

  it('sair derruba a sessão e devolve para o login', async () => {
    apiMock.getAccount
      .mockResolvedValueOnce(conta({ accessStatus: 'PENDENTE' }))
      .mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));

    renderGate();
    await userEvent.click(await screen.findByRole('button', { name: /^sair$/i }));

    expect(apiMock.logout).toHaveBeenCalled();
    expect(await screen.findByRole('link', { name: /entrar com google/i })).toBeInTheDocument();
  });

  it('sair que falha diz isso, em vez de fingir que a sessão acabou', async () => {
    apiMock.getAccount.mockResolvedValue(conta({ accessStatus: 'PENDENTE' }));
    apiMock.logout.mockRejectedValue(new Error('502 Bad Gateway'));

    renderGate();
    await userEvent.click(await screen.findByRole('button', { name: /^sair$/i }));

    // A sessão continua viva no servidor: recarregar a conta faria a pessoa achar que saiu.
    expect(await screen.findByText(/não foi possível sair.*502/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /solicitação registrada/i })).toBeInTheDocument();
  });

  it('excluir a conta pede confirmação e só então apaga', async () => {
    apiMock.getAccount
      .mockResolvedValueOnce(conta({ accessStatus: 'PENDENTE' }))
      .mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));

    renderGate();
    await userEvent.click(
      await screen.findByRole('button', { name: /cancelar e apagar meus dados/i }),
    );

    // Um clique não apaga: a confirmação diz o que se perde antes de existir botão que apaga.
    expect(apiMock.deleteAccount).not.toHaveBeenCalled();
    expect(screen.getByText(/não dá para desfazer/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /sim, excluir tudo/i }));

    await waitFor(() => expect(apiMock.deleteAccount).toHaveBeenCalled());
    expect(await screen.findByRole('link', { name: /entrar com google/i })).toBeInTheDocument();
  });

  it('API fora do ar não vira tela de login: o erro é dito, com nova tentativa', async () => {
    apiMock.getAccount.mockRejectedValueOnce(new Error('Failed to fetch'));

    renderGate();

    expect(await screen.findByText(/não foi possível falar com a API/i)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /entrar com google/i })).not.toBeInTheDocument();

    apiMock.getAccount.mockResolvedValue(conta());
    await userEvent.click(screen.getByRole('button', { name: /tentar de novo/i }));

    expect(await screen.findByText(APP)).toBeInTheDocument();
  });
});

/**
 * Demonstração vencida (#62).
 *
 * O servidor responde 401, igual a quem nunca entrou — e é a resposta certa: passado o prazo a
 * conta não existe mais para quem está do outro lado. Quem sabe que havia uma demonstração é o
 * navegador, e é isso que separa "faça login" de "a demonstração acabou".
 */
describe('AuthGate com demonstração vencida', () => {
  const MARCA = 'fos.demo-conta';

  it('sem marca de demonstração, 401 continua sendo a tela de login', async () => {
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));

    renderGate();

    expect(
      await screen.findByRole('heading', { name: /entrar no fightossstreak/i }),
    ).toBeInTheDocument();
  });

  it('com marca, a tela explica o que aconteceu em vez de pedir login', async () => {
    sessionStorage.setItem(MARCA, 'sim');
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));

    renderGate();

    expect(
      await screen.findByRole('heading', { name: /a demonstração terminou/i }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: /entrar no fightossstreak/i }),
    ).not.toBeInTheDocument();
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

  it('quem quer conta de verdade limpa a marca e cai no login', async () => {
    sessionStorage.setItem(MARCA, 'sim');
    apiMock.getAccount.mockRejectedValue(new ApiError(401, 'nao_autenticado', 'sem sessão'));

    renderGate();
    await screen.findByRole('heading', { name: /a demonstração terminou/i });

    await userEvent.click(screen.getByRole('button', { name: /entrar na minha conta/i }));

    // Sem limpar a marca, o 401 seguinte cairia de novo nesta mesma tela — e a pessoa ficaria
    // presa num loop de demonstração sem nunca ver o login.
    expect(
      await screen.findByRole('heading', { name: /entrar no fightossstreak/i }),
    ).toBeInTheDocument();
    expect(sessionStorage.getItem(MARCA)).toBeNull();
  });
});
