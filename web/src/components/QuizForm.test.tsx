import { ApiError } from '@fos/api-client';
import type { QuizQuestionView, QuizResult, QuizSubmission } from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { QuizForm } from './QuizForm.tsx';

/**
 * O quiz monta a submissão, trata o `quiz_unavailable` — estado esperado nos 35 nós sem quiz
 * escrito (D15) — e exibe as explicações, que é onde mora o valor de retenção.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    submitQuiz: vi.fn<(code: string, submission: QuizSubmission) => Promise<QuizResult>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const PERGUNTAS: readonly QuizQuestionView[] = [
  {
    id: 1,
    prompt: 'O que faz a fuga de quadril funcionar?',
    options: [
      { id: 11, label: 'Criar espaço com o quadril' },
      { id: 12, label: 'Puxar a cabeça' },
    ],
  },
  {
    id: 2,
    prompt: 'Qual a base da postura em pé?',
    options: [
      { id: 21, label: 'Joelhos flexionados' },
      { id: 22, label: 'Pernas esticadas' },
    ],
  },
];

beforeEach(() => {
  vi.clearAllMocks();
});

describe('QuizForm', () => {
  it('envia uma resposta por pergunta, com a alternativa marcada', async () => {
    apiMock.submitQuiz.mockResolvedValue({
      score: 100,
      correctCount: 2,
      totalQuestions: 2,
      passed: true,
      passingScore: 70,
      feedback: [],
    });
    const onDone = vi.fn();

    render(<QuizForm nodeCode="M0.1" questions={PERGUNTAS} onDone={onDone} />);

    await userEvent.click(screen.getByRole('radio', { name: /criar espaço com o quadril/i }));
    await userEvent.click(screen.getByRole('radio', { name: /joelhos flexionados/i }));
    await userEvent.click(screen.getByRole('button', { name: /responder/i }));

    expect(apiMock.submitQuiz).toHaveBeenCalledWith('M0.1', {
      answers: [
        { questionId: 1, optionId: 11 },
        { questionId: 2, optionId: 21 },
      ],
    });
    await waitFor(() => expect(onDone).toHaveBeenCalledOnce());
  });

  it('só libera o envio depois de todas as perguntas respondidas', async () => {
    render(<QuizForm nodeCode="M0.1" questions={PERGUNTAS} onDone={vi.fn()} />);

    const enviar = screen.getByRole('button', { name: /responder/i });
    expect(enviar).toBeDisabled();

    await userEvent.click(screen.getByRole('radio', { name: /criar espaço com o quadril/i }));
    expect(enviar).toBeDisabled();

    await userEvent.click(screen.getByRole('radio', { name: /joelhos flexionados/i }));
    expect(enviar).toBeEnabled();
  });

  it('mostra a nota e a explicação de cada pergunta, inclusive nos acertos', async () => {
    // A explicação no acerto não é redundância: é o que transforma "acertei" em "entendi por quê".
    apiMock.submitQuiz.mockResolvedValue({
      score: 50,
      correctCount: 1,
      totalQuestions: 2,
      passed: false,
      passingScore: 70,
      feedback: [
        {
          questionId: 1,
          prompt: 'O que faz a fuga de quadril funcionar?',
          correct: true,
          explanation: 'O espaço vem do quadril, não do braço.',
        },
        {
          questionId: 2,
          prompt: 'Qual a base da postura em pé?',
          correct: false,
          explanation: 'Perna esticada entrega a queda.',
        },
      ],
    });

    render(<QuizForm nodeCode="M0.1" questions={PERGUNTAS} onDone={vi.fn()} />);
    await userEvent.click(screen.getByRole('radio', { name: /criar espaço com o quadril/i }));
    await userEvent.click(screen.getByRole('radio', { name: /pernas esticadas/i }));
    await userEvent.click(screen.getByRole('button', { name: /responder/i }));

    expect(await screen.findByText(/50\/100 — 1 de 2 corretas/)).toBeInTheDocument();
    expect(screen.getByText(/O espaço vem do quadril/)).toBeInTheDocument();
    expect(screen.getByText(/Perna esticada entrega a queda/)).toBeInTheDocument();
    expect(screen.getByText(/Mínimo para concluir: 70/)).toBeInTheDocument();
  });

  it('nó sem quiz escrito é estado esperado, não erro', () => {
    // 35 dos 46 nós estão nessa situação hoje (D15). Tratar como falha faria o app parecer
    // quebrado na maior parte da árvore.
    render(<QuizForm nodeCode="M5.1" questions={[]} onDone={vi.fn()} />);

    expect(screen.getByText(/quiz ainda não escrito para este nó/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /responder/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/erro/i)).not.toBeInTheDocument();
  });

  it('quiz_unavailable vindo do backend vira explicação, não mensagem de erro crua', async () => {
    apiMock.submitQuiz.mockRejectedValue(
      new ApiError(409, 'quiz_unavailable', 'No quiz for node M0.1'),
    );

    render(<QuizForm nodeCode="M0.1" questions={PERGUNTAS} onDone={vi.fn()} />);
    await userEvent.click(screen.getByRole('radio', { name: /criar espaço com o quadril/i }));
    await userEvent.click(screen.getByRole('radio', { name: /joelhos flexionados/i }));
    await userEvent.click(screen.getByRole('button', { name: /responder/i }));

    expect(await screen.findByText(/ainda não tem quiz escrito/i)).toBeInTheDocument();
    expect(screen.queryByText(/No quiz for node/)).not.toBeInTheDocument();
  });

  it('falha de rede exibe a mensagem e mantém o formulário respondido', async () => {
    apiMock.submitQuiz.mockRejectedValue(new Error('Failed to fetch'));
    const onDone = vi.fn();

    render(<QuizForm nodeCode="M0.1" questions={PERGUNTAS} onDone={onDone} />);
    await userEvent.click(screen.getByRole('radio', { name: /criar espaço com o quadril/i }));
    await userEvent.click(screen.getByRole('radio', { name: /joelhos flexionados/i }));
    await userEvent.click(screen.getByRole('button', { name: /responder/i }));

    expect(await screen.findByText(/Failed to fetch/)).toBeInTheDocument();
    // Perder as respostas por causa de uma falha de rede obrigaria a refazer o quiz inteiro.
    expect(screen.getByRole('radio', { name: /criar espaço com o quadril/i })).toBeChecked();
    expect(onDone).not.toHaveBeenCalled();
  });

  it('refazer o quiz limpa as respostas anteriores', async () => {
    apiMock.submitQuiz.mockResolvedValue({
      score: 50,
      correctCount: 1,
      totalQuestions: 2,
      passed: false,
      passingScore: 70,
      feedback: [],
    });

    render(<QuizForm nodeCode="M0.1" questions={PERGUNTAS} onDone={vi.fn()} />);
    await userEvent.click(screen.getByRole('radio', { name: /criar espaço com o quadril/i }));
    await userEvent.click(screen.getByRole('radio', { name: /joelhos flexionados/i }));
    await userEvent.click(screen.getByRole('button', { name: /responder/i }));

    await userEvent.click(await screen.findByRole('button', { name: /refazer o quiz/i }));

    expect(screen.getByRole('radio', { name: /criar espaço com o quadril/i })).not.toBeChecked();
    expect(screen.getByRole('button', { name: /responder/i })).toBeDisabled();
  });
});
