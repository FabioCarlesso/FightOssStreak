import { useEffect, useState } from 'react';
import { api } from '../api/client.ts';

/**
 * Anotação fixada do nó — o detalhe que você quer reler toda vez que abre a técnica.
 *
 * Fica junto do conceito, e não no fim da página com o histórico, porque as duas respondem coisas
 * diferentes: o conceito é do currículo e vale para qualquer praticante; esta é sua, e é a pista de
 * recuperação mais forte que o app tem (mesmo argumento da D32 para os clipes da própria academia).
 * Enterrá-la embaixo do vídeo seria transformá-la em arquivo morto.
 *
 * Edição inline, sem modal nem tela nova: anotar é gesto de um segundo no meio da revisão.
 */
export function PinnedNote({
  nodeCode,
  note,
  readOnly = false,
  onSaved,
}: {
  nodeCode: string;
  note?: string;
  readOnly?: boolean;
  onSaved: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(note ?? '');
  const [saving, setSaving] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  // O que esta tela acabou de gravar vence o `note` recebido até a recarga do nó chegar. Sem isto
  // a anotação antiga reapareceria por um round-trip depois de salvar, que é exatamente a janela
  // em que se olha para conferir se salvou.
  const [justSaved, setJustSaved] = useState<string | null | undefined>(undefined);
  const current = justSaved !== undefined ? justSaved : (note ?? null);

  useEffect(() => {
    setJustSaved(undefined);
  }, [note]);

  async function save() {
    setSaving(true);
    setFailure(null);
    try {
      const saved = await api.savePinnedNote(nodeCode, draft);
      setJustSaved(saved.note ?? null);
      setEditing(false);
      onSaved();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setSaving(false);
    }
  }

  // Em demonstração nada é gravado (D31): sem anotação a seção nem aparece, e com anotação ela é
  // exibida sem o caminho de edição.
  if (readOnly) {
    return current ? (
      <aside className="pinned-note">
        <h4 className="pinned-note__label">Sua anotação</h4>
        <p className="pinned-note__text">{current}</p>
      </aside>
    ) : null;
  }

  if (!editing) {
    return (
      <aside className="pinned-note">
        <h4 className="pinned-note__label">Sua anotação</h4>
        {current ? (
          <p className="pinned-note__text">{current}</p>
        ) : (
          <p className="pinned-note__empty">
            O detalhe que você sempre esquece nesta técnica fica aqui, à vista toda vez que abrir o
            nó.
          </p>
        )}
        <button
          type="button"
          className="pinned-note__action"
          onClick={() => {
            setDraft(current ?? '');
            setEditing(true);
          }}
        >
          {current ? 'Editar anotação' : 'Anotar'}
        </button>
      </aside>
    );
  }

  return (
    <aside className="pinned-note">
      <label className="pinned-note__label" htmlFor={`pinned-note-${nodeCode}`}>
        Sua anotação
      </label>
      <textarea
        id={`pinned-note-${nodeCode}`}
        className="pinned-note__input"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        rows={3}
        maxLength={1000}
        placeholder="Ex.: o cotovelo tem que estar colado antes de virar o quadril."
      />
      {failure && <p className="error">{failure}</p>}
      <div className="pinned-note__buttons">
        <button type="button" onClick={() => void save()} disabled={saving}>
          {saving ? 'Salvando…' : 'Salvar'}
        </button>
        <button
          type="button"
          className="pinned-note__action"
          onClick={() => {
            setEditing(false);
            setFailure(null);
          }}
        >
          Cancelar
        </button>
      </div>
      {/* Apagar o texto e salvar limpa a anotação — é o mesmo gesto, sem um botão só para isso. */}
      <p className="hint">Salvar com o campo vazio remove a anotação.</p>
    </aside>
  );
}
