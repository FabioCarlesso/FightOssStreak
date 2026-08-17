import type { DrillRequest, DrillResult, SrsView } from '@fos/types';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DrillForm } from './DrillForm.tsx';

/**
 * O drill é o registro de "treinei isso hoje" e o que alimenta o SM-2. O preview do próximo
 * intervalo é calculado no cliente com `@fos/domain`, a mesma regra do backend, para responder no
 * clique — e tem uma regra sutil: acima de 2 repetições ele não promete número.
 *
 * Os valores de intervalo do SM-2 já são travados em `shared/domain` e espelhados no backend
 * (D17). O que se testa aqui é o comportamento do componente, não a regra.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    logDrill: vi.fn<(code: string, drill: DrillRequest) => Promise<DrillResult>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const RESULTADO: DrillResult = {
  nextReviewOn: '2026-03-12',
  streak: { currentStreak: 4 },
};

beforeEach(() => {
  vi.clearAllMocks();
  // O preview chama `new Date()` direto. Sem relógio fixo, um teste que roda perto da meia-noite
  // calcularia a data de um dia e a asserção de outro.
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-03-10T09:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('DrillForm', () => {
  it('trocar a auto-avaliação muda o preview do próximo intervalo', async () => {
    const srs: SrsView = {
      scheduled: true,
      nextReviewOn: '2026-03-10',
      intervalDays: 2,
      repetitions: 1,
    };

    render(<DrillForm nodeCode="M0.1" srs={srs} onDone={vi.fn()} />);

    // 'OK' é o padrão do formulário.
    expect(screen.getByText(/volta em 5 dias/i)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: /não lembrava/i }));

    // Lapso reinicia a contagem: a técnica volta amanhã.
    expect(screen.getByText(/volta em 1 dias/i)).toBeInTheDocument();
    expect(screen.queryByText(/volta em 5 dias/i)).not.toBeInTheDocument();
  });

  it('acima de 2 repetições não promete intervalo nenhum', () => {
    // O fator de facilidade real vive no backend; a partir daí o cálculo local chutaria 2.5 e
    // mostraria um número errado com cara de certo. Melhor não prometer.
    const srs: SrsView = {
      scheduled: true,
      nextReviewOn: '2026-03-10',
      intervalDays: 12,
      repetitions: 3,
    };

    render(<DrillForm nodeCode="M0.1" srs={srs} onDone={vi.fn()} />);

    expect(screen.queryByText(/volta em/i)).not.toBeInTheDocument();
  });

  it('nó ainda não agendado mostra preview a partir do zero', () => {
    render(<DrillForm nodeCode="M0.1" onDone={vi.fn()} />);

    expect(screen.getByText(/volta em 2 dias/i)).toBeInTheDocument();
  });

  it('registrar envia a auto-avaliação e a anotação, e mostra a próxima revisão', async () => {
    apiMock.logDrill.mockResolvedValue(RESULTADO);
    const onDone = vi.fn();

    render(<DrillForm nodeCode="M0.1" onDone={onDone} />);

    await userEvent.click(screen.getByRole('radio', { name: /saiu limpo/i }));
    await userEvent.type(screen.getByLabelText(/anotação/i), 'travou no gancho');
    await userEvent.click(screen.getByRole('button', { name: /treinei isso hoje/i }));

    expect(apiMock.logDrill).toHaveBeenCalledWith('M0.1', {
      recall: 'EASY',
      note: 'travou no gancho',
    });
    expect(await screen.findByText(/2026-03-12/)).toBeInTheDocument();
    expect(screen.getByText(/4 dia\(s\)/)).toBeInTheDocument();
    expect(onDone).toHaveBeenCalledOnce();
  });

  it('anotação em branco não é enviada como string vazia', async () => {
    apiMock.logDrill.mockResolvedValue(RESULTADO);

    render(<DrillForm nodeCode="M0.1" onDone={vi.fn()} />);
    await userEvent.type(screen.getByLabelText(/anotação/i), '   ');
    await userEvent.click(screen.getByRole('button', { name: /treinei isso hoje/i }));

    expect(apiMock.logDrill).toHaveBeenCalledWith('M0.1', { recall: 'OK', note: undefined });
  });

  it('falha de rede exibe a mensagem sem perder o que foi digitado', async () => {
    apiMock.logDrill.mockRejectedValue(new Error('Failed to fetch'));
    const onDone = vi.fn();

    render(<DrillForm nodeCode="M0.1" onDone={onDone} />);

    const anotacao = screen.getByLabelText(/anotação/i);
    await userEvent.type(anotacao, 'o professor corrigiu a base');
    await userEvent.click(screen.getByRole('button', { name: /treinei isso hoje/i }));

    expect(await screen.findByText(/Failed to fetch/)).toBeInTheDocument();
    // Redigitar a anotação depois de uma falha de rede é o tipo de atrito que faz parar de anotar.
    expect(anotacao).toHaveValue('o professor corrigiu a base');
    expect(onDone).not.toHaveBeenCalled();
  });
});
