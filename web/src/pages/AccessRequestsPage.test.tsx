import type { AccessRequests, AccountView } from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountContext } from '../state/account.ts';
import { AccessRequestsPage } from './AccessRequestsPage.tsx';

/**
 * A fila é onde alguém entra ou não entra no app.
 *
 * O que se testa aqui é a consequência de cada clique — aprovar não pode virar recusar, e a lista
 * precisa refletir a decisão sem F5 —, não o layout da lista.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getAccessRequests: vi.fn<() => Promise<AccessRequests>>(),
    approveAccessRequest: vi.fn<(id: number) => Promise<AccessRequests>>(),
    denyAccessRequest: vi.fn<(id: number) => Promise<AccessRequests>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

function renderPage(owner = true) {
  const account: AccountView = {
    displayName: 'Dono',
    email: 'dono@example.test',
    provider: 'google',
    accessStatus: 'APROVADO',
    owner,
  };
  return render(
    <AccountContext.Provider value={{ account, reload: vi.fn() }}>
      <AccessRequestsPage />
    </AccountContext.Provider>,
  );
}

const FILA: AccessRequests = {
  requests: [
    {
      id: 7,
      displayName: 'Novato',
      email: 'novato@example.test',
      provider: 'google',
      requestedAt: '2026-08-19T12:00:00Z',
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  apiMock.getAccessRequests.mockResolvedValue(FILA);
  apiMock.approveAccessRequest.mockResolvedValue({ requests: [] });
  apiMock.denyAccessRequest.mockResolvedValue({ requests: [] });
});

describe('AccessRequestsPage', () => {
  it('lista quem está esperando, com e-mail e provedor', async () => {
    renderPage();

    expect(await screen.findByText('Novato')).toBeInTheDocument();
    expect(screen.getByText(/novato@example\.test · google/)).toBeInTheDocument();
  });

  it('aprovar libera a conta e tira a linha da fila', async () => {
    apiMock.getAccessRequests.mockResolvedValueOnce(FILA).mockResolvedValue({ requests: [] });

    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: /aprovar/i }));

    expect(apiMock.approveAccessRequest).toHaveBeenCalledWith(7);
    expect(apiMock.denyAccessRequest).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByText('Novato')).not.toBeInTheDocument());
  });

  it('recusar chama a recusa, não a aprovação', async () => {
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: /recusar/i }));

    expect(apiMock.denyAccessRequest).toHaveBeenCalledWith(7);
    expect(apiMock.approveAccessRequest).not.toHaveBeenCalled();
  });

  it('falha ao decidir aparece na tela em vez de sumir', async () => {
    apiMock.approveAccessRequest.mockRejectedValue(new Error('403 Forbidden'));

    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: /aprovar/i }));

    expect(await screen.findByText(/403 Forbidden/)).toBeInTheDocument();
    expect(screen.getByText('Novato')).toBeInTheDocument();
  });

  it('fila vazia diz isso, em vez de mostrar uma lista sem nada', async () => {
    apiMock.getAccessRequests.mockResolvedValue({ requests: [] });

    renderPage();

    expect(await screen.findByText(/ninguém esperando/i)).toBeInTheDocument();
  });

  it('conta comum não vê a fila nem chama a API', async () => {
    renderPage(false);

    expect(await screen.findByText(/tela é do dono do app/i)).toBeInTheDocument();
    expect(apiMock.getAccessRequests).not.toHaveBeenCalled();
  });
});
