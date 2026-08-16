import { useState } from 'react';
import type { QuizQuestionView, QuizResult } from '@fos/types';
import { ApiError, api } from '../api/client.ts';

/**
 * Quiz conceitual.
 *
 * O gabarito só chega no resultado, junto com a explicação de cada pergunta — o valor de retenção
 * está na explicação, não na nota. Por isso o feedback é sempre exibido, inclusive nos acertos.
 */
export function QuizForm({
  nodeCode,
  questions,
  onDone,
}: {
  nodeCode: string;
  questions: readonly QuizQuestionView[];
  onDone: () => void;
}) {
  const [answers, setAnswers] = useState<Record<number, number>>({});
  const [result, setResult] = useState<QuizResult | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  if (questions.length === 0) {
    return (
      <p className="empty">
        Quiz ainda não escrito para este nó. A curadoria começou por M0 e M1 — registrar o drill
        abaixo já conclui nós sem quiz.
      </p>
    );
  }

  const allAnswered = questions.every((question) => answers[question.id ?? -1] !== undefined);

  async function submit() {
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

      <button type="submit" disabled={!allAnswered || submitting}>
        {submitting ? 'Corrigindo…' : 'Responder'}
      </button>
      {!allAnswered && <p className="hint">Responda todas as perguntas para enviar.</p>}
    </form>
  );
}

function QuizFeedback({ result, onRetry }: { result: QuizResult; onRetry: () => void }) {
  return (
    <div className="quiz-result">
      <p className={result.passed ? 'quiz-result__score' : 'quiz-result__score quiz-result__score--fail'}>
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
