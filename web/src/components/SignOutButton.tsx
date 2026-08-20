import { useState } from 'react';
import { api } from '../api/client.ts';

/**
 * Sair da conta.
 *
 * Quem derruba a sessão é o servidor; a tela só reage. Daí o `onSignedOut` ser chamado **apenas**
 * quando o logout deu certo: se ele falha, a sessão continua viva, e recarregar a conta faria a
 * pessoa achar que saiu sem ter saído. A falha é dita em vez de virar promessa rejeitada em
 * silêncio.
 */
export function SignOutButton({
  onSignedOut,
  className,
}: {
  onSignedOut: () => void;
  className?: string;
}) {
  const [leaving, setLeaving] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  async function signOut() {
    setLeaving(true);
    setFailure(null);
    try {
      await api.logout();
      onSignedOut();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setLeaving(false);
    }
  }

  return (
    <>
      <button type="button" className={className} onClick={() => void signOut()} disabled={leaving}>
        {leaving ? 'Saindo…' : 'Sair'}
      </button>
      {failure && <span className="gate__error">Não foi possível sair: {failure}</span>}
    </>
  );
}
