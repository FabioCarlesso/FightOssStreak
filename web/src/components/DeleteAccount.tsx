import { useState } from 'react';
import { api } from '../api/client.ts';

/**
 * Exclusão da própria conta.
 *
 * Não é item de polimento: app com login precisa ter rota de deleção, e a exigência é da própria
 * loja da Apple (docs/02-publicacao-ios-desafios.md) — por isso ela entrou junto com o login, e não
 * depois. Vale também para quem está na fila ou foi recusado: pediu acesso e não entrou, tem o
 * mesmo direito de sumir do banco.
 *
 * A confirmação é em dois passos de propósito. O texto diz o que se perde, porque um botão que
 * apaga streak, progresso e agenda de revisão não pode ser do tamanho de um "cancelar".
 */
export function DeleteAccount({ onDeleted, label }: { onDeleted: () => void; label?: string }) {
  const [confirming, setConfirming] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  async function remove() {
    setDeleting(true);
    setFailure(null);
    try {
      await api.deleteAccount();
      onDeleted();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
      setDeleting(false);
    }
  }

  if (!confirming) {
    return (
      <button type="button" className="danger" onClick={() => setConfirming(true)}>
        {label ?? 'Excluir minha conta'}
      </button>
    );
  }

  return (
    <div className="danger-zone">
      <p>
        Isto apaga <strong>a conta e tudo que é dela</strong>: progresso na árvore, streak, agenda
        de revisão, drills registrados, anotações e o aceite do aviso. Não dá para desfazer.
      </p>
      {failure && <p className="gate__error">{failure}</p>}
      <div className="danger-zone__actions">
        <button type="button" className="danger" onClick={() => void remove()} disabled={deleting}>
          {deleting ? 'Excluindo…' : 'Sim, excluir tudo'}
        </button>
        <button type="button" onClick={() => setConfirming(false)} disabled={deleting}>
          Cancelar
        </button>
      </div>
    </div>
  );
}
