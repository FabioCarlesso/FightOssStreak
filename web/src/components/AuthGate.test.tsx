import type { AccountView, AuthProviders } from '@fos/types';
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
  apiMock.getAuthProviders.mockResolvedValue({
    providers: [
      { id: 'google', label: 'Google', authorizationUrl: '/api/oauth2/authorization/google' },
    ],
  });
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
    apiMock.getAuthProviders.mockResolvedValue({ providers: [] });

    renderGate();

    expect(
      await screen.findByText(/nenhum provedor de login está configurado/i),
    ).toBeInTheDocument();
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
