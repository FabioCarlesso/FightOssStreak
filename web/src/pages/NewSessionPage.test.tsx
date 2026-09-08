import type { ReviewAgenda, TrainingSession, TrainingSessionRequest } from '@fos/types';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { NewSessionPage } from './NewSessionPage.tsx';
import { DemoModeProvider } from '../state/DemoModeProvider.tsx';

/**
 * Os dois fluxos de registro do diário (#114, D56).
 *
 * **Isto é extensão consciente da D29**, que disse "teste de UI cobre três fluxos, e só três". O
 * critério dela era cobrir o que decide se o app é utilizável, e o diário passou a ser a porta de
 * entrada do registro: se salvar só com a data não funcionar, ou se a técnica marcada não viajar
 * junto, o que quebra não é uma tela — é o insumo do streak (D58) e do SRS ao mesmo tempo. Os dois
 * casos aqui são exatamente esses, e nenhum deles é teste de layout.
 *
 * O que **não** está aqui, pela mesma D29: a regra de streak e a de SRS. Elas já são espelhadas
 * entre `shared/domain` e o backend com valores fixados (D17), e refazê-las pela UI cobriria a
 * mesma coisa por um caminho mais frágil.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getReviewsToday: vi.fn<() => Promise<ReviewAgenda>>(),
    createTrainingSession: vi.fn<(s: TrainingSessionRequest) => Promise<TrainingSession>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const navegou = vi.fn();
vi.mock('react-router-dom', async () => {
  const real = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...real, useNavigate: () => navegou };
});

const AGENDA: ReviewAgenda = {
  today: '2026-03-10',
  dueCount: 1,
  due: [{ nodeCode: 'M0.1', title: 'Queda de base', daysOverdue: 2 }],
};

function renderPage() {
  return render(
    <MemoryRouter>
      <DemoModeProvider>
        <NewSessionPage />
      </DemoModeProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  apiMock.getReviewsToday.mockResolvedValue(AGENDA);
  apiMock.createTrainingSession.mockResolvedValue({ id: 7, trainedOn: '2026-03-10' });
  // A data padrão do formulário sai de `new Date()`. Sem relógio fixo, um teste que roda perto da
  // meia-noite montaria o corpo com a data de um dia e afirmaria a de outro.
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-03-10T09:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
});

describe('NewSessionPage', () => {
  it('registra um treino só com a data, sem tocar em nenhum outro campo', async () => {
    renderPage();

    await userEvent.click(screen.getByRole('button', { name: /registrar treino/i }));

    // Só a data é obrigatória, e ela já vem preenchida: um toque basta. É o desenho inteiro da
    // tela — atrito é o que faz o registro não acontecer.
    expect(apiMock.createTrainingSession).toHaveBeenCalledTimes(1);
    const corpo = apiMock.createTrainingSession.mock.calls[0]![0];
    expect(corpo.trainedOn).toBe('2026-03-10');
    expect(corpo.kind).toBe('AULA');
    expect(corpo.tecnicas).toEqual([]);
    // Campo em branco vira ausente, e não string vazia ou zero: no backend, ausente é "não
    // informado", e um zero em duração diria que o treino durou zero minutos.
    expect(corpo.durationMinutes).toBeUndefined();
    expect(corpo.weightKg).toBeUndefined();
    expect(corpo.feeling).toBeUndefined();
    expect(navegou).toHaveBeenCalledWith('/diario/7');
  });

  it('a técnica marcada na agenda viaja junto, com a auto-avaliação escolhida', async () => {
    renderPage();

    // A agenda do dia é a lista de técnicas: quem acabou de treinar não deveria ter que lembrar o
    // código do nó. Nenhuma vem marcada — marcar por padrão registraria treino que não houve.
    const tecnica = await screen.findByRole('checkbox', { name: /queda de base/i });
    expect(tecnica).not.toBeChecked();

    await userEvent.click(tecnica);
    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: /como saiu m0\.1/i }),
      'HARD',
    );
    await userEvent.click(screen.getByRole('button', { name: /registrar treino/i }));

    expect(apiMock.createTrainingSession.mock.calls[0]![0].tecnicas).toEqual([
      { nodeCode: 'M0.1', recall: 'HARD' },
    ]);
  });

  it('descanso não oferece técnica: seria dia sem treino com treino registrado', async () => {
    renderPage();

    await userEvent.click(screen.getByRole('radio', { name: /descanso/i }));

    expect(screen.getByText(/descanso é dia sem treino/i)).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: /queda de base/i })).not.toBeInTheDocument();
  });
});
