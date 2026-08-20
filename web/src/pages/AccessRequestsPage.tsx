import { useState } from 'react';
import { api } from '../api/client.ts';
import { useAccount } from '../state/account.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * Fila de solicitações de acesso, do lado de quem decide.
 *
 * Só o dono do app chega aqui — quem decide é o backend, contra `fos.auth.owner-emails`; esconder o
 * link no menu é conveniência, não controle de acesso.
 */
export function AccessRequestsPage() {
  const { account } = useAccount();
  // O `useAsync` roda antes de qualquer `return`, como todo hook — sem a guarda aqui, conta comum
  // dispararia uma chamada só para receber 403.
  const queue = useAsync(
    () => (account.owner ? api.getAccessRequests() : Promise.resolve({ requests: [] })),
    [account.owner],
  );
  const [deciding, setDeciding] = useState<number | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  if (!account.owner) {
    return <p className="empty">Esta tela é do dono do app.</p>;
  }

  async function decide(id: number, approve: boolean) {
    setDeciding(id);
    setFailure(null);
    try {
      await (approve ? api.approveAccessRequest(id) : api.denyAccessRequest(id));
      queue.reload();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setDeciding(null);
    }
  }

  const requests = queue.data?.requests ?? [];

  return (
    <div className="stack">
      <section className="card">
        <header className="card__header">
          <h2>Solicitações de acesso</h2>
          <span className="badge">{requests.length}</span>
        </header>

        {queue.loading && !queue.data && <p className="empty">Carregando…</p>}
        {queue.error && <p className="error">{queue.error.message}</p>}
        {failure && <p className="error">{failure}</p>}

        {!queue.loading && requests.length === 0 && (
          <p className="empty">
            Ninguém esperando. Contas novas aparecem aqui ao entrar pela primeira vez.
          </p>
        )}

        <ul className="requests">
          {requests.map((request) => (
            <li key={request.id} className="requests__item">
              <div>
                <p className="requests__name">{request.displayName}</p>
                <p className="requests__meta">
                  {request.email ?? 'sem e-mail'} · {request.provider}
                </p>
              </div>
              <div className="requests__actions">
                <button
                  type="button"
                  onClick={() => void decide(request.id ?? 0, true)}
                  disabled={deciding === request.id}
                >
                  Aprovar
                </button>
                <button
                  type="button"
                  className="danger"
                  onClick={() => void decide(request.id ?? 0, false)}
                  disabled={deciding === request.id}
                >
                  Recusar
                </button>
              </div>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
