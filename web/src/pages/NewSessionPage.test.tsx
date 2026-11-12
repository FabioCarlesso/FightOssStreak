import type { ReviewAgenda, TrainingSession, TrainingSessionRequest, TreeView } from '@fos/types';
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
    getTree: vi.fn<() => Promise<TreeView>>(),
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

/** Árvore mínima com posições e apelidos — o que a busca de técnica usa (#194). */
const ARVORE: TreeView = {
  modules: [
    {
      code: 'M3',
      title: 'Guarda aberta',
      nodes: [
        {
          code: 'M3.6',
          title: 'Meia-guarda: conceito',
          positionCodes: ['MEIA_GUARDA'],
          aliases: [],
        },
        {
          code: 'M3.7',
          title: 'Raspagem de meia-guarda',
          positionCodes: ['MEIA_GUARDA'],
          aliases: [],
        },
      ],
    },
    {
      code: 'M4',
      title: 'Controle superior',
      nodes: [
        {
          code: 'M4.1',
          title: '100kg: manutenção e pressão',
          positionCodes: ['CEM_QUILOS'],
          aliases: [],
        },
        {
          code: 'M7.1',
          title: 'Kimura',
          positionCodes: ['CEM_QUILOS'],
          aliases: ['chave de ombro'],
        },
      ],
    },
  ],
  positions: [
    { code: 'MEIA_GUARDA', name: 'Meia-guarda', aliases: ['half guard'], family: 'GUARDA' },
    {
      code: 'CEM_QUILOS',
      name: '100kg',
      aliases: ['lateral', 'side control'],
      family: 'CONTROLE_POR_CIMA',
    },
  ],
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
  localStorage.clear();
  apiMock.getReviewsToday.mockResolvedValue(AGENDA);
  apiMock.getTree.mockResolvedValue(ARVORE);
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

  it('o kimono da última aula já vem marcado, viaja junto e fica lembrado (#193)', async () => {
    localStorage.setItem('fos.ultimoKimono', 'NOGI');
    apiMock.createTrainingSession.mockResolvedValue({
      id: 8,
      trainedOn: '2026-03-10',
      attire: 'GI',
    });
    renderPage();

    // Registrar a aula não pode custar um toque a mais: o último valor usado vem marcado.
    expect(screen.getByRole('radio', { name: /sem kimono/i })).toBeChecked();

    await userEvent.click(screen.getByRole('radio', { name: /com kimono/i }));
    await userEvent.click(screen.getByRole('button', { name: /registrar treino/i }));

    expect(apiMock.createTrainingSession.mock.calls[0]![0].attire).toBe('GI');
    expect(localStorage.getItem('fos.ultimoKimono')).toBe('GI');
  });

  it('físico não oferece kimono, e o que estava marcado não vai junto', async () => {
    localStorage.setItem('fos.ultimoKimono', 'GI');
    renderPage();

    await userEvent.click(screen.getByRole('radio', { name: /físico/i }));
    expect(screen.queryByRole('radio', { name: /com kimono/i })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /registrar treino/i }));
    const corpo = apiMock.createTrainingSession.mock.calls[0]![0];
    expect(corpo.kind).toBe('FISICO');
    expect(corpo.attire).toBeUndefined();
  });

  it('"Não dizer" esquece o kimono lembrado, e físico não mexe na lembrança', async () => {
    localStorage.setItem('fos.ultimoKimono', 'GI');
    renderPage();

    await userEvent.click(screen.getByRole('radio', { name: /não dizer/i, checked: false }));
    await userEvent.click(screen.getByRole('button', { name: /registrar treino/i }));
    expect(localStorage.getItem('fos.ultimoKimono')).toBeNull();
  });

  it('sessão de físico não apaga o kimono da última aula', async () => {
    localStorage.setItem('fos.ultimoKimono', 'NOGI');
    renderPage();

    await userEvent.click(screen.getByRole('radio', { name: /físico/i }));
    await userEvent.click(screen.getByRole('button', { name: /registrar treino/i }));
    expect(localStorage.getItem('fos.ultimoKimono')).toBe('NOGI');
  });

  it('a técnica fora da agenda é achada pelo apelido e entra marcada (#194)', async () => {
    renderPage();

    // "side control" não está em título nenhum: é apelido da posição de onde M4.1 e M7.1 partem.
    await userEvent.type(
      await screen.findByRole('searchbox', { name: /buscar técnica/i }),
      'side control',
    );
    expect(screen.getByRole('button', { name: /M4\.1/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /M7\.1/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /M3\.6/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /M7\.1/ }));

    // Escolher pela busca já é a decisão: a técnica entra marcada, sem segundo toque.
    expect(screen.getByRole('checkbox', { name: /kimura/i })).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: /registrar treino/i }));
    expect(apiMock.createTrainingSession.mock.calls[0]![0].tecnicas).toEqual([
      { nodeCode: 'M7.1', recall: 'OK' },
    ]);
  });

  it('o filtro por posição lista só as técnicas daquela posição (#194)', async () => {
    renderPage();

    await userEvent.selectOptions(
      await screen.findByRole('combobox', { name: /posição/i }),
      'MEIA_GUARDA',
    );

    expect(screen.getByRole('button', { name: /M3\.6/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /M3\.7/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /M4\.1/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /M7\.1/ })).not.toBeInTheDocument();
  });
});
