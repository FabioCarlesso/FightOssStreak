import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import type { Recall } from '@fos/domain';
import type { TrainingSession } from '@fos/types';
import { api } from '../api/client.ts';
import { SessionFields } from '../components/SessionFields.tsx';
import {
  type SessionFieldsState,
  emptySessionFields,
  toSessionBody,
} from '../state/sessionFields.ts';
import { formatDay, sessionKindLabel, todayIso } from '../content/diario.ts';
import { RECALL_LABELS, recallLabel } from '../content/recall.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * Detalhe e edição de uma sessão (#114, D56).
 *
 * É aqui que o "completa depois" acontece: a pessoa registrou "treinei" no vestiário e volta em
 * casa para escrever o que aprendeu e vincular as técnicas. Vincular retroage no SRS e, se o dia
 * estava perdoado por freeze, devolve o saldo (D55e) — nada disso é reimplementado na tela.
 *
 * Não há excluir sessão, e é escopo declarado: desfazer envolveria desfazer SRS, progresso e
 * freeze. Correção é por edição — e desvincular técnica, que é o que de fato se erra, tem botão.
 */
export function SessionPage() {
  const { id = '' } = useParams();
  const sessionId = Number(id);
  const session = useAsync(() => api.getTrainingSession(sessionId), [sessionId]);

  const [fields, setFields] = useState<SessionFieldsState | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const carregada = session.data;

  // O formulário nasce do que veio do servidor, e só quando o id da tela e o do dado batem: o
  // `useAsync` preserva o dado anterior de propósito, e editar sob a URL nova gravaria na sessão
  // errada — o mesmo defeito que o `NodePage` já documenta.
  useEffect(() => {
    if (carregada?.id === sessionId) setFields(toFields(carregada));
  }, [carregada, sessionId]);

  if (session.error && !carregada) return <p className="error">{session.error.message}</p>;
  if (!carregada || carregada.id !== sessionId || !fields) {
    return <p className="empty">Carregando treino…</p>;
  }

  async function salvar() {
    if (!fields) return;
    setSaving(true);
    setFailure(null);
    try {
      await api.updateTrainingSession(sessionId, toSessionBody(fields));
      setSaved(true);
      session.reload();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="stack">
      <section className="card">
        <header className="card__header">
          <h2>
            {sessionKindLabel(carregada.kind)} ·{' '}
            <time dateTime={carregada.trainedOn}>{formatDay(carregada.trainedOn)}</time>
          </h2>
          <Link to="/diario">Voltar ao diário</Link>
        </header>

        <form
          onSubmit={(event) => {
            event.preventDefault();
            setSaved(false);
            void salvar();
          }}
        >
          <SessionFields value={fields} onChange={setFields} today={todayIso()} />
          {failure && <p className="error">{failure}</p>}
          {saved && <p className="hint">Salvo.</p>}
          <button type="submit" disabled={saving}>
            {saving ? 'Salvando…' : 'Salvar'}
          </button>
        </form>
      </section>

      <TechniqueSection session={carregada} onChanged={session.reload} />
    </div>
  );
}

function TechniqueSection({
  session,
  onChanged,
}: {
  session: TrainingSession;
  onChanged: () => void;
}) {
  const [nodeCode, setNodeCode] = useState('');
  const [recall, setRecall] = useState<Recall>('OK');
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const descanso = session.kind === 'DESCANSO';

  async function executar(acao: () => Promise<unknown>) {
    setBusy(true);
    setFailure(null);
    try {
      await acao();
      onChanged();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="card">
      <h2>Técnicas vinculadas</h2>

      {(session.tecnicas?.length ?? 0) === 0 && (
        <p className="empty">Nenhuma técnica do currículo neste treino — e isso é normal.</p>
      )}

      <ul className="diary-technique">
        {session.tecnicas?.map((tecnica, index) => (
          <li key={`${tecnica.nodeCode}-${index}`}>
            <Link to={`/no/${tecnica.nodeCode}`}>{tecnica.nodeCode}</Link> {tecnica.nodeTitle}{' '}
            <span className="pill">{recallLabel(tecnica.recall)}</span>
            {tecnica.note && <span className="diary-technique__note">{tecnica.note}</span>}
            <button
              type="button"
              className="link-button"
              disabled={busy}
              onClick={() =>
                void executar(() =>
                  api.removeSessionTechnique(session.id ?? 0, tecnica.nodeCode ?? ''),
                )
              }
            >
              Desvincular
            </button>
          </li>
        ))}
      </ul>

      {descanso ? (
        <p className="hint">Descanso é dia sem treino: não recebe técnica.</p>
      ) : (
        <form
          className="session-link"
          onSubmit={(event) => {
            event.preventDefault();
            const code = nodeCode.trim().toUpperCase();
            if (!code) return;
            void executar(async () => {
              await api.addSessionTechnique(session.id ?? 0, { nodeCode: code, recall });
              setNodeCode('');
            });
          }}
        >
          <label className="session-form__field">
            <span>Código do nó</span>
            <input
              value={nodeCode}
              onChange={(event) => setNodeCode(event.target.value)}
              placeholder="M0.1"
            />
          </label>
          <label className="session-form__field">
            <span>Como saiu</span>
            <select value={recall} onChange={(event) => setRecall(event.target.value as Recall)}>
              {RECALL_LABELS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" disabled={busy || !nodeCode.trim()}>
            Vincular
          </button>
        </form>
      )}

      {failure && <p className="error">{failure}</p>}
      <p className="hint">
        Desvincular não apaga o treino: o drill continua no histórico do nó, só deixa de pertencer a
        esta sessão.
      </p>
    </section>
  );
}

function toFields(session: TrainingSession): SessionFieldsState {
  return {
    ...emptySessionFields(session.trainedOn ?? todayIso()),
    kind: session.kind ?? 'AULA',
    durationMinutes: session.durationMinutes != null ? String(session.durationMinutes) : '',
    feeling: session.feeling ?? '',
    weightKg: session.weightKg != null ? String(session.weightKg) : '',
    learned: session.learned ?? '',
    improve: session.improve ?? '',
  };
}
