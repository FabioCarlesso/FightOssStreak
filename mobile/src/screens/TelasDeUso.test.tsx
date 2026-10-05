import { router } from 'expo-router';
import { fireEvent, screen } from '@testing-library/react-native';

import { apiFalsa, comProvedores } from '../test/fakes';
import { TelaDiario } from './TelaDiario';
import { TelaHoje } from './TelaHoje';
import { TelaNo } from './TelaNo';
import { TelaNovaSessao } from './TelaNovaSessao';

const NO = {
  code: 'M1.1',
  title: 'Fuga de montada',
  moduleCode: 'M1',
  moduleTitle: 'Sobrevivência',
  status: 'AVAILABLE',
  belt: 'BRANCA',
  concept: 'Primeiro parágrafo.\n\nSegundo parágrafo.',
  video: {
    catalogued: true,
    youtubeId: 'abc123',
    title: 'Upa',
    channel: 'Canal do Professor',
    watchUrl: 'https://www.youtube.com/watch?v=abc123',
  },
  quiz: [
    {
      id: 1,
      prompt: 'Qual é o objetivo?',
      options: [
        { id: 10, label: 'Sobreviver' },
        { id: 11, label: 'Finalizar' },
      ],
    },
  ],
  srs: { scheduled: false },
  safetyNotice: 'Conteúdo de apoio ao estudo. Respeite o tap sempre.',
};

describe('Hoje', () => {
  it('mostra os números do backend sem recalcular nada, e a agenda com atraso', async () => {
    const api = apiFalsa({
      getStreak: jest.fn().mockResolvedValue({
        currentStreak: 4,
        longestStreak: 9,
        activeDaysLast30: 7,
        targetDaysLast30: 12,
        freezesPerMonth: 2,
        freezesRemaining: 1,
      }),
      getReviewsToday: jest.fn().mockResolvedValue({
        dueCount: 2,
        due: [
          { nodeCode: 'M1.1', title: 'Fuga de montada', daysOverdue: 0 },
          { nodeCode: 'M1.2', title: 'Fuga de 100kg', daysOverdue: 3 },
        ],
      }),
    });
    await comProvedores(<TelaHoje />, { api });

    expect(await screen.findByTestId('streak-atual')).toHaveTextContent(/^4\s*dias seguidos/);
    expect(screen.getByTestId('dias-ativos')).toHaveTextContent(
      '7 de 12 dias com treino registrado nos últimos 30',
    );
    expect(screen.getByText('Recorde: 9 dias')).toBeOnTheScreen();
    expect(await screen.findByText('3 dias atrasado')).toBeOnTheScreen();
    expect(screen.getByText('para hoje')).toBeOnTheScreen();
    // O histórico do heatmap só é pedido depois do streak, que é quem grava o freeze (D55).
    expect(api.getStreakHistory).toHaveBeenCalled();
  });
});

describe('Nó', () => {
  it('credita o canal do vídeo e mostra o aviso curto', async () => {
    await comProvedores(<TelaNo codigo="M1.1" />, {
      api: apiFalsa({ getNode: jest.fn().mockResolvedValue(NO) }),
    });

    expect(await screen.findByTestId('credito-video')).toHaveTextContent(
      'Upa — canal Canal do Professor',
    );
    expect(screen.getByText('Segundo parágrafo.')).toBeOnTheScreen();
    expect(screen.getByTestId('aviso-curto')).toHaveTextContent(/Respeite o tap/);
  });

  it('responde o quiz e mostra a nota com a explicação', async () => {
    const submitQuiz = jest.fn().mockResolvedValue({
      score: 100,
      correctCount: 1,
      totalQuestions: 1,
      passed: true,
      passingScore: 70,
      feedback: [
        { questionId: 1, correct: true, prompt: 'Qual é o objetivo?', explanation: 'Porque sim.' },
      ],
    });
    const api = apiFalsa({ getNode: jest.fn().mockResolvedValue(NO), submitQuiz });
    await comProvedores(<TelaNo codigo="M1.1" />, { api });

    await fireEvent.press(await screen.findByRole('radio', { name: 'Sobreviver' }));
    await fireEvent.press(screen.getByRole('button', { name: 'Responder' }));

    expect(await screen.findByTestId('nota-quiz')).toHaveTextContent('100/100 — 1 de 1 corretas');
    expect(screen.getByText('Porque sim.')).toBeOnTheScreen();
    expect(submitQuiz).toHaveBeenCalledWith('M1.1', {
      answers: [{ questionId: 1, optionId: 10 }],
    });
  });

  it('nó bloqueado não oferece quiz nem drill', async () => {
    await comProvedores(<TelaNo codigo="M1.1" />, {
      api: apiFalsa({ getNode: jest.fn().mockResolvedValue({ ...NO, status: 'LOCKED' }) }),
    });

    expect(await screen.findByText(/Nó bloqueado/)).toBeOnTheScreen();
    expect(screen.queryByText('Quiz conceitual')).toBeNull();
    expect(screen.queryByText('Registrar drill')).toBeNull();
  });
});

describe('Registrar treino', () => {
  it('grava a sessão com a técnica marcada e volta', async () => {
    const createTrainingSession = jest.fn().mockResolvedValue({ id: 7 });
    const api = apiFalsa({
      createTrainingSession,
      getReviewsToday: jest.fn().mockResolvedValue({
        dueCount: 1,
        due: [{ nodeCode: 'M1.1', title: 'Fuga de montada' }],
      }),
    });
    await comProvedores(<TelaNovaSessao />, { api });

    await fireEvent.changeText(screen.getByLabelText('Peso (kg)'), '78,4');
    await fireEvent.press(await screen.findByRole('radio', { name: 'M1.1 Fuga de montada' }));
    await fireEvent.press(screen.getByRole('radio', { name: 'Travei' }));
    await fireEvent.press(screen.getByRole('button', { name: 'Registrar treino' }));

    expect(createTrainingSession).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'AULA',
        weightKg: 78.4,
        durationMinutes: undefined,
        tecnicas: [{ nodeCode: 'M1.1', recall: 'HARD' }],
      }),
    );
    expect(router.back).toHaveBeenCalled();
  });

  it('descanso não recebe técnica', async () => {
    const createTrainingSession = jest.fn().mockResolvedValue({ id: 8 });
    await comProvedores(<TelaNovaSessao />, { api: apiFalsa({ createTrainingSession }) });

    await fireEvent.press(screen.getByRole('radio', { name: 'Descanso' }));
    expect(screen.queryByText('Técnicas do currículo')).toBeNull();
    await fireEvent.press(screen.getByRole('button', { name: 'Registrar treino' }));

    expect(createTrainingSession).toHaveBeenCalledWith(
      expect.objectContaining({ kind: 'DESCANSO', tecnicas: [] }),
    );
  });

  it('data futura ou malformada não deixa registrar', async () => {
    await comProvedores(<TelaNovaSessao />);

    await fireEvent.changeText(screen.getByLabelText('Data * (AAAA-MM-DD)'), '2999-01-01');

    expect(screen.getByText('Use uma data no formato AAAA-MM-DD, até hoje.')).toBeOnTheScreen();
    expect(screen.getByRole('button', { name: 'Registrar treino' })).toBeDisabled();
  });
});

describe('Diário', () => {
  it('mostra peso e sensação como foram anotados, sem interpretar (D57)', async () => {
    const api = apiFalsa({
      getDiary: jest.fn().mockResolvedValue({
        sessionsInMonth: 1,
        days: [
          {
            day: '2026-10-04',
            countsAsTrainingDay: true,
            sessions: [
              { id: 1, kind: 'AULA', weightKg: 78.4, feeling: 'MAL', durationMinutes: 90 },
            ],
            avulsos: [],
          },
        ],
      }),
    });
    await comProvedores(<TelaDiario />, { api });

    const dia = await screen.findByText('04/10/2026');
    expect(dia).toBeOnTheScreen();
    expect(screen.getByText('78.4 kg')).toBeOnTheScreen();
    expect(screen.getByText('Mal')).toBeOnTheScreen();
    expect(screen.getByTestId('contagem-mes')).toHaveTextContent(/^1 treino registrado neste mês/);
  });
});
