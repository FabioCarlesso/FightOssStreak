import { type ReactNode, useState } from 'react';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';
import { FULL_DISCLAIMER } from '../content/disclaimer.ts';

/**
 * Portão de aceite do aviso de responsabilidade.
 *
 * O aceite é por *versão de texto*: quando o texto muda materialmente, o backend passa a reportar
 * `accepted: false` e o aviso reaparece. É requisito de produto, não enfeite
 * (docs/06-disclaimer-responsabilidade.md).
 */
export function DisclaimerGate({ children }: { children: ReactNode }) {
  const status = useAsync(() => api.getDisclaimer(), []);
  const [accepting, setAccepting] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  if (status.loading) {
    return <p className="empty">Carregando…</p>;
  }

  if (status.error) {
    return (
      <div className="gate">
        <div className="gate__card">
          <h2>Não foi possível falar com a API</h2>
          <p className="gate__error">{status.error.message}</p>
          <p>
            O backend está rodando? <code>npm run dev:backend</code>
          </p>
          <button type="button" onClick={status.reload}>
            Tentar de novo
          </button>
        </div>
      </div>
    );
  }

  if (status.data?.accepted) {
    return <>{children}</>;
  }

  const version = status.data?.currentVersion;

  async function accept() {
    if (!version) return;
    setAccepting(true);
    setFailure(null);
    try {
      await api.acceptDisclaimer(version);
      status.reload();
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setAccepting(false);
    }
  }

  return (
    <div className="gate">
      <div className="gate__card gate__card--wide">
        <h2>Aviso importante — leia antes de usar</h2>
        <div className="gate__text">
          {FULL_DISCLAIMER.map((paragraph) => (
            <p key={paragraph.slice(0, 40)}>{paragraph}</p>
          ))}
        </div>
        {failure && <p className="gate__error">{failure}</p>}
        <button type="button" onClick={() => void accept()} disabled={accepting}>
          {accepting ? 'Registrando…' : 'Li e concordo'}
        </button>
        <p className="gate__version">Versão do texto: {version}</p>
      </div>
    </div>
  );
}
