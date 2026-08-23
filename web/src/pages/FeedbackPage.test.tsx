import type { AccountView, FeedbackList, FeedbackView } from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountContext } from '../state/account.ts';
import { FeedbackPage } from './FeedbackPage.tsx';

/**
 * O que importa aqui: quem manda não vê a fila, quem decide (o dono) vê e muda o status, e a
 * conta de demonstração (D39) não tem como mandar — ela não tem identidade nem prazo para
 * responder a quem enviou.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    submitFeedback: vi.fn<() => Promise<FeedbackView>>(),
    getFeedbackQueue: vi.fn<() => Promise<FeedbackList>>(),
    decideFeedback: vi.fn<() => Promise<FeedbackView>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

function renderPage(overrides: Partial<AccountView> = {}) {
  const account: AccountView = {
    displayName: 'Aluno',
    email: 'aluno@example.test',
    provider: 'google',
    accessStatus: 'APROVADO',
    owner: false,
    ...overrides,
  };
  return render(
    <AccountContext.Provider value={{ account, reload: vi.fn() }}>
      <FeedbackPage />
    </AccountContext.Provider>,
  );
}

const FILA: FeedbackList = {
  items: [
    {
      id: 9,
      category: 'BUG',
      nodeCode: undefined,
      message: 'app trava ao abrir',
      status: 'ABERTO',
      createdAt: '2026-08-19T12:00:00Z',
      authorLabel: 'Aluno',
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  apiMock.submitFeedback.mockResolvedValue({
    id: 1,
    category: 'BUG',
    message: 'algo',
    status: 'ABERTO',
  });
  apiMock.getFeedbackQueue.mockResolvedValue(FILA);
  apiMock.decideFeedback.mockResolvedValue({ ...FILA.items![0], status: 'RESOLVIDO' });
});

describe('FeedbackPage', () => {
  it('conta comum manda feedback e não vê a fila do dono', async () => {
    renderPage();

    expect(screen.queryByText('Fila de feedback')).not.toBeInTheDocument();
    expect(apiMock.getFeedbackQueue).not.toHaveBeenCalled();

    await userEvent.type(screen.getByLabelText('Mensagem'), 'Seria bom ter modo escuro.');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(apiMock.submitFeedback).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'Seria bom ter modo escuro.' }),
    );
    expect(await screen.findByText(/enviado/i)).toBeInTheDocument();
  });

  it('conta de demonstração não vê o formulário', () => {
    renderPage({ demoExpiresAt: '2026-08-16T12:00:00Z' });

    expect(screen.getByText(/não manda feedback/i)).toBeInTheDocument();
    expect(screen.queryByLabelText('Mensagem')).not.toBeInTheDocument();
  });

  it('o dono vê a fila e muda o status para resolvido', async () => {
    renderPage({ owner: true });

    expect(await screen.findByText('app trava ao abrir')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Resolvido' }));

    expect(apiMock.decideFeedback).toHaveBeenCalledWith(9, 'RESOLVIDO');
    await waitFor(() => expect(apiMock.getFeedbackQueue).toHaveBeenCalledTimes(2));
  });

  it('falha ao decidir aparece na tela em vez de sumir', async () => {
    renderPage({ owner: true });
    apiMock.decideFeedback.mockRejectedValue(new Error('403 Forbidden'));

    await userEvent.click(await screen.findByRole('button', { name: 'Resolvido' }));

    expect(await screen.findByText(/403 Forbidden/)).toBeInTheDocument();
  });
});
