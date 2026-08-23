import { useState } from 'react';
import type { FeedbackCategory } from '@fos/types';
import { api } from '../api/client.ts';
import {
  FEEDBACK_CATEGORY_LABELS,
  feedbackCategoryLabel,
  feedbackStatusLabel,
} from '../content/feedback.ts';
import { useAccount } from '../state/account.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * Feedback de usuário: bug, conteúdo errado, troca de vídeo, sugestão (docs/13-feedback-usuarios.md).
 *
 * Formulário único e genérico — sem botão contextual por nó nesta fatia (docs/13). Quem manda não
 * decide: a fila abaixo só aparece para o dono, mesmo modelo da `AccessRequestsPage`.
 */
export function FeedbackPage() {
  const { account } = useAccount();
  const isDemo = account.demoExpiresAt != null;

  return (
    <div className="stack">
      {isDemo ? (
        <section className="card">
          <h2>Feedback</h2>
          <p className="empty">
            A conta de demonstração não manda feedback — peça acesso para ter uma conta de verdade.
          </p>
        </section>
      ) : (
        <FeedbackForm />
      )}
      {account.owner && <FeedbackQueue />}
    </div>
  );
}

function FeedbackForm() {
  const [category, setCategory] = useState<FeedbackCategory>('BUG');
  const [nodeCode, setNodeCode] = useState('');
  const [message, setMessage] = useState('');
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  async function submit() {
    setSending(true);
    setFailure(null);
    try {
      await api.submitFeedback({
        category,
        nodeCode: nodeCode.trim() || undefined,
        message: message.trim(),
      });
      setMessage('');
      setNodeCode('');
      setSent(true);
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setSending(false);
    }
  }

  return (
    <section className="card">
      <h2>Enviar feedback</h2>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          setSent(false);
          void submit();
        }}
      >
        <label>
          <span>Categoria</span>
          <select
            value={category}
            onChange={(event) => setCategory(event.target.value as FeedbackCategory)}
          >
            {FEEDBACK_CATEGORY_LABELS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <label>
          <span>Nó do currículo (opcional)</span>
          <input
            type="text"
            value={nodeCode}
            onChange={(event) => setNodeCode(event.target.value)}
            placeholder="Ex.: M0.3"
            maxLength={16}
          />
        </label>

        <label>
          <span>Mensagem</span>
          <textarea
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            rows={4}
            maxLength={2000}
            required
          />
        </label>

        {failure && <p className="error">{failure}</p>}
        {sent && <p className="hint">Feedback enviado. Obrigado!</p>}

        <button type="submit" disabled={sending || message.trim().length === 0}>
          {sending ? 'Enviando…' : 'Enviar'}
        </button>
      </form>
    </section>
  );
}

/** Fila de feedback, do lado de quem decide — só o dono do app chega aqui. */
function FeedbackQueue() {
  const queue = useAsync(() => api.getFeedbackQueue(), []);
  const [deciding, setDeciding] = useState<number | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  async function decide(id: number, status: 'EM_ANALISE' | 'RESOLVIDO' | 'RECUSADO') {
    setDeciding(id);
    setFailure(null);
    try {
      await api.decideFeedback(id, status);
      queue.reload();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setDeciding(null);
    }
  }

  const items = queue.data?.items ?? [];

  return (
    <section className="card">
      <header className="card__header">
        <h2>Fila de feedback</h2>
        <span className="badge">{items.length}</span>
      </header>

      {queue.loading && !queue.data && <p className="empty">Carregando…</p>}
      {queue.error && <p className="error">{queue.error.message}</p>}
      {failure && <p className="error">{failure}</p>}

      {!queue.loading && items.length === 0 && <p className="empty">Nenhum feedback ainda.</p>}

      <ul className="requests">
        {items.map((item) => (
          <li key={item.id} className="requests__item">
            <div>
              <p className="requests__name">
                {feedbackCategoryLabel(item.category)}
                {item.nodeCode ? ` · ${item.nodeCode}` : ''}
              </p>
              <p className="requests__meta">
                {item.authorLabel} · {feedbackStatusLabel(item.status)}
              </p>
              <p>{item.message}</p>
            </div>
            <div className="requests__actions">
              <button
                type="button"
                onClick={() => void decide(item.id ?? 0, 'EM_ANALISE')}
                disabled={deciding === item.id}
              >
                Em análise
              </button>
              <button
                type="button"
                onClick={() => void decide(item.id ?? 0, 'RESOLVIDO')}
                disabled={deciding === item.id}
              >
                Resolvido
              </button>
              <button
                type="button"
                className="danger"
                onClick={() => void decide(item.id ?? 0, 'RECUSADO')}
                disabled={deciding === item.id}
              >
                Recusar
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
