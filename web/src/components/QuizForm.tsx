import { useState } from 'react';
import type { QuizQuestionView, QuizResult } from '@fos/types';
import { ApiError, api } from '../api/client.ts';

/**
 * Quiz conceitual.
 *
 * O gabarito só chega no resultado, junto com a explicação de cada pergunta — o valor de retenção
 * está na explicação, não na nota. Por isso o feedback é sempre exibido, inclusive nos acertos.
 *
 * `readOnly` é o quiz do modo demonstração (D31): perguntas e alternativas visíveis, envio fora.
 * A garantia de "a demonstração não grava" mora aqui — `QuizService.submit` não valida bloqueio,
 * então não há rede de segurança no servidor.
 */
export function QuizForm({
  nodeCode,
  questions,
  onDone,
  readOnly = false,
}: {
  nodeCode: string;
  questions: readonly QuizQuestionView[];
  onDone: () => void;
  readOnly?: boolean;
}) {
  const [answers, setAnswers] = useState<Record<number, number>>({});
  const [result, setResult] = useState<QuizResult | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  if (questions.length === 0) {
    // Em modo leitura o registro de drill não é exibido, então apontar para "abaixo" mandaria o
    // leitor procurar uma seção que não existe.
    return (
      <p className="empty">
        Quiz ainda não escrito para este nó. A curadoria começou por M0 e M1 —{' '}
        {readOnly
          ? 'aqui há só conceito e vídeo para revisar.'
          : 'registrar o drill abaixo já conclui nós sem quiz.'}
      </p>
    );
  }

  const allAnswered = questions.every((question) => answers[question.id ?? -1] !== undefined);

  async function submit() {
    if (readOnly) return;
    setSubmitting(true);
    setFailure(null);
    try {
      const payload = {
        answers: questions.map((question) => ({
          questionId: question.id as number,
          optionId: answers[question.id ?? -1] as number,
        })),
      };
      setResult(await api.submitQuiz(nodeCode, payload));
      onDone();
    } catch (cause) {
      setFailure(
        cause instanceof ApiError && cause.isQuizUnavailable
          ? 'Este nó ainda não tem quiz escrito.'
          : cause instanceof Error
            ? cause.message
            : String(cause),
      );
    } finally {
      setSubmitting(false);
    }
  }

  function retry() {
    setResult(null);
    setAnswers({});
  }

  if (result) {
    return <QuizFeedback result={result} onRetry={retry} />;
  }

  return (
    <form
      className="quiz"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      {questions.map((question, index) => (
        <fieldset key={question.id} className="quiz__question">
          <legend>
            {index + 1}. {question.prompt}
          </legend>
          {question.options?.map((option) => (
            <label key={option.id} className="quiz__option">
              <input
                type="radio"
                name={`q-${question.id}`}
                disabled={readOnly}
                checked={answers[question.id ?? -1] === option.id}
                onChange={() =>
                  setAnswers((current) => ({
                    ...current,
                    [question.id as number]: option.id as number,
                  }))
                }
              />
              <span>{option.label}</span>
            </label>
          ))}
        </fieldset>
      ))}

      {failure && <p className="error">{failure}</p>}

      <button type="submit" disabled={readOnly || !allAnswered || submitting}>
        {submitting ? 'Corrigindo…' : 'Responder'}
      </button>
      {readOnly ? (
        <p className="hint">
          Modo demonstração: o quiz deste nó está aberto só para leitura. Responder concluiria o nó
          e mexeria no progresso, no streak e na agenda de revisão.
        </p>
      ) : (
        !allAnswered && <p className="hint">Responda todas as perguntas para enviar.</p>
      )}
    </form>
  );
}

function QuizFeedback({ result, onRetry }: { result: QuizResult; onRetry: () => void }) {
  return (
    <div className="quiz-result">
      <p
        className={
          result.passed ? 'quiz-result__score' : 'quiz-result__score quiz-result__score--fail'
        }
      >
        {result.score}/100 — {result.correctCount} de {result.totalQuestions} corretas
      </p>
      <p className="hint">
        {result.passed
          ? 'Nó concluído. O primeiro drill de revisão já foi agendado.'
          : `Mínimo para concluir: ${result.passingScore}. Leia as explicações e refaça quando quiser.`}
      </p>

      <ul className="quiz-result__list">
        {result.feedback?.map((item) => (
          <li key={item.questionId} className={item.correct ? 'correct' : 'wrong'}>
            <p className="quiz-result__prompt">
              {item.correct ? '✓' : '✗'} {item.prompt}
            </p>
            <p className="quiz-result__explanation">{item.explanation}</p>
          </li>
        ))}
      </ul>

      <button type="button" onClick={onRetry}>
        Refazer o quiz
      </button>
    </div>
  );
}
