import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { Recall } from '@fos/domain';
import { api } from '../api/client.ts';
import { SessionFields } from '../components/SessionFields.tsx';
import {
  type SessionFieldsState,
  emptySessionFields,
  toSessionBody,
} from '../state/sessionFields.ts';
import { RECALL_LABELS } from '../content/recall.ts';
import { todayIso } from '../content/diario.ts';
import { useDemoMode } from '../state/demoMode.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * Registrar um treino (#114, D56).
 *
 * A tela é desenhada em torno de um risco de produto, e não de um requisito de dado: **atrito é o
 * que faz o registro não acontecer**. Por isso só a data é obrigatória, ela já vem preenchida com
 * hoje, e salvar com um toque é caminho completo — o resto se completa depois, sem estado de
 * rascunho.
 *
 * As técnicas vêm pré-listadas com o que o SRS marcou para hoje: quem acabou de treinar não deveria
 * ter que lembrar o código do nó. Nenhuma vem marcada — marcar por padrão registraria treino que
 * não houve, e é o SRS que pagaria a conta.
 */
export function NewSessionPage() {
  const navigate = useNavigate();
  const demo = useDemoMode();
  const today = todayIso();
  const [fields, setFields] = useState<SessionFieldsState>(() => emptySessionFields(today));
  const [tecnicas, setTecnicas] = useState<Record<string, Recall>>({});
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const agenda = useAsync(() => api.getReviewsToday(), []);

  // Modo demonstração não grava (D31): registrar aqui mexeria em streak, SRS e progresso de
  // verdade, que é exatamente o que a inspeção do currículo não pode fazer.
  if (demo.enabled) {
    return (
      <section className="card">
        <h2>Registrar treino</h2>
        <p className="empty">
          O modo demonstração não grava. Desligue a faixa no topo para registrar um treino de
          verdade.
        </p>
      </section>
    );
  }

  const marcadas = Object.keys(tecnicas);

  function toggle(nodeCode: string) {
    setTecnicas((atual) => {
      const proximo = { ...atual };
      if (proximo[nodeCode]) delete proximo[nodeCode];
      else proximo[nodeCode] = 'OK';
      return proximo;
    });
  }

  async function submit() {
    setSaving(true);
    setFailure(null);
    try {
      const criada = await api.createTrainingSession({
        ...toSessionBody(fields),
        tecnicas: marcadas.map((nodeCode) => ({ nodeCode, recall: tecnicas[nodeCode]! })),
      });
      navigate(`/diario/${criada.id}`);
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setSaving(false);
    }
  }

  const descanso = fields.kind === 'DESCANSO';

  return (
    <form
      className="stack"
      onSubmit={(event) => {
        event.preventDefault();
        void submit();
      }}
    >
      <section className="card">
        <header className="card__header">
          <h2>Registrar treino</h2>
          <Link to="/diario">Voltar ao diário</Link>
        </header>
        <p className="hint">
          Só a data é obrigatória. Salve agora e complete depois — o registro incompleto já conta.
        </p>

        <SessionFields value={fields} onChange={setFields} today={today} />
      </section>

      <section className="card">
        <header className="card__header">
          <h2>Técnicas do currículo</h2>
          {agenda.data && <span className="badge">{agenda.data.dueCount ?? 0}</span>}
        </header>

        {descanso ? (
          <p className="empty">
            Descanso é dia sem treino: não recebe técnica. Troque o tipo acima para vincular.
          </p>
        ) : (
          <>
            <p className="hint">
              O que o SRS marcou para hoje. Marcar aqui é o mesmo que registrar o drill na tela do
              nó: reagenda a revisão e move o progresso.
            </p>

            {agenda.loading && !agenda.data && <p className="empty">Carregando agenda…</p>}
            {agenda.data && agenda.data.due?.length === 0 && (
              <p className="empty">
                Nada vencido hoje. Dá para vincular técnicas depois, pela tela da sessão.
              </p>
            )}

            <ul className="session-techniques">
              {agenda.data?.due?.map((item) => {
                const marcada = tecnicas[item.nodeCode ?? ''] != null;
                return (
                  <li key={item.nodeCode} className="session-techniques__item">
                    <label>
                      <input
                        type="checkbox"
                        checked={marcada}
                        onChange={() => toggle(item.nodeCode ?? '')}
                      />
                      <span className="due-list__code">{item.nodeCode}</span> {item.title}
                    </label>
                    {marcada && (
                      <select
                        aria-label={`Como saiu ${item.nodeCode}`}
                        value={tecnicas[item.nodeCode ?? '']}
                        onChange={(event) =>
                          setTecnicas((atual) => ({
                            ...atual,
                            [item.nodeCode ?? '']: event.target.value as Recall,
                          }))
                        }
                      >
                        {RECALL_LABELS.map((option) => (
                          <option key={option.value} value={option.value}>
                            {option.label}
                          </option>
                        ))}
                      </select>
                    )}
                  </li>
                );
              })}
            </ul>
          </>
        )}
      </section>

      {failure && <p className="error">{failure}</p>}

      <button type="submit" disabled={saving}>
        {saving ? 'Registrando…' : 'Registrar treino'}
      </button>
    </form>
  );
}
