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
 * decide: a fila abaixo só aparece para quem administra (D48).
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
      {account.role === 'ADMIN' && <FeedbackQueue />}
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
      <p className="hint">
        Bug, conteúdo errado, vídeo que não serve ou ideia de funcionalidade — tudo cai na mesma
        fila.
      </p>
      <form
        className="feedback-form"
        onSubmit={(event) => {
          event.preventDefault();
          setSent(false);
          void submit();
        }}
      >
        <div className="feedback-form__row">
          <label className="feedback-form__field">
            <span className="feedback-form__label">Categoria</span>
            <select
              className="feedback-form__control"
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

          <label className="feedback-form__field">
            <span className="feedback-form__label">Nó do currículo (opcional)</span>
            <input
              className="feedback-form__control"
              type="text"
              value={nodeCode}
              onChange={(event) => setNodeCode(event.target.value)}
              placeholder="Ex.: M0.3"
              maxLength={16}
            />
          </label>
        </div>

        <label className="feedback-form__field">
          <span className="feedback-form__label">Mensagem</span>
          <textarea
            className="feedback-form__control"
            value={message}
            onChange={(event) => setMessage(event.target.value)}
            rows={4}
            maxLength={2000}
            required
          />
        </label>

        <div className="feedback-form__actions">
          <button type="submit" disabled={sending || message.trim().length === 0}>
            {sending ? 'Enviando…' : 'Enviar'}
          </button>
          {failure && <p className="error">{failure}</p>}
          {sent && <p className="hint">Feedback enviado. Obrigado!</p>}
        </div>
      </form>
    </section>
  );
}

type Decisao = { status: 'EM_ANALISE' | 'RESOLVIDO' | 'RECUSADO'; label: string; danger?: boolean };

/** As três saídas de um feedback, na ordem em que costumam acontecer. */
const DECISOES: ReadonlyArray<Decisao> = [
  { status: 'EM_ANALISE', label: 'Em análise' },
  { status: 'RESOLVIDO', label: 'Resolvido' },
  { status: 'RECUSADO', label: 'Recusar', danger: true },
];

/** `EM_ANALISE` vira `--em-analise`: status desconhecido fica com a chip neutra, sem quebrar. */
function statusClass(status: string | undefined): string {
  const base = 'feedback-item__status';
  if (!status || status === 'ABERTO') return base;
  return `${base} ${base}--${status.toLowerCase().replace(/_/g, '-')}`;
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

      <ul className="feedback-queue">
        {items.map((item) => (
          <li key={item.id} className="feedback-item">
            <div className="feedback-item__head">
              <p className="feedback-item__category">{feedbackCategoryLabel(item.category)}</p>
              {item.nodeCode && <span className="feedback-item__node">{item.nodeCode}</span>}
              <span className={statusClass(item.status)}>{feedbackStatusLabel(item.status)}</span>
            </div>
            <p className="feedback-item__meta">{item.authorLabel}</p>
            <p className="feedback-item__message">{item.message}</p>
            <div className="feedback-item__actions">
              {DECISOES.map((decisao) => (
                <button
                  key={decisao.status}
                  type="button"
                  className={[
                    'feedback-item__action',
                    decisao.danger ? 'danger' : '',
                    item.status === decisao.status ? 'feedback-item__action--atual' : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  onClick={() => void decide(item.id ?? 0, decisao.status)}
                  disabled={deciding === item.id || item.status === decisao.status}
                >
                  {decisao.label}
                </button>
              ))}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
