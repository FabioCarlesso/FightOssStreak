import { useCallback, useState } from 'react';
import { api } from '../api/client.ts';

const STORAGE_KEY = 'fos.demo-conta';

/**
 * Conta de demonstração (#62) — o degrau **antes** do portão.
 *
 * Não confundir com `demoMode.ts`, que é o modo de inspeção do autor **dentro** do app (D31):
 * aquele ignora pré-requisitos e não grava nada; este é uma sessão de verdade, numa conta de
 * verdade, que grava de verdade e some sozinha. Os dois se chamam "demonstração" no vocabulário do
 * projeto, e é por isso que os nomes aqui dizem `conta`.
 *
 * A marca em `sessionStorage` existe por um motivo só: quando o prazo vence, o servidor responde
 * **401**, igual a quem nunca entrou — é a resposta certa, porque para quem está do outro lado a
 * conta já não existe. Sem esta marca a tela mostraria "entrar" para quem estava no meio de uma
 * demonstração, sem dizer o que aconteceu.
 */
export function markDemoSession(): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, 'sim');
  } catch {
    // Armazenamento bloqueado: a sessão de demonstração funciona igual, só que o fim dela vira a
    // tela de login comum em vez da explicação. Nada quebra.
  }
}

export function hadDemoSession(): boolean {
  try {
    return sessionStorage.getItem(STORAGE_KEY) === 'sim';
  } catch {
    return false;
  }
}

export function clearDemoSession(): void {
  try {
    sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // idem
  }
}

export interface StartDemo {
  readonly start: () => void;
  readonly starting: boolean;
  readonly failure: string | null;
}

/**
 * Abre uma demonstração e avisa para onde ir.
 *
 * O destino vem do servidor, e não fixo aqui: quem sabe o que a sessão recém-aberta já pode ver é
 * quem a abriu. `onStarted` existe porque os dois lugares que chamam isto precisam de coisas
 * diferentes — a landing navega, e a tela de demonstração vencida só recarrega a conta.
 */
export function useStartDemo(onStarted: (destino: string) => void): StartDemo {
  const [starting, setStarting] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);

  const start = useCallback(() => {
    setStarting(true);
    setFailure(null);
    api
      .startDemo()
      .then((session) => {
        markDemoSession();
        onStarted(session.destino ?? '/hoje');
      })
      .catch((cause: unknown) => {
        setFailure(cause instanceof Error ? cause.message : String(cause));
        setStarting(false);
      });
  }, [onStarted]);

  return { start, starting, failure };
}

/**
 * Quanto tempo resta, em texto curto.
 *
 * Arredonda para minuto e nunca mostra negativo: no instante em que o prazo vira, o que a pessoa
 * vê é a tela de demonstração encerrada, não uma faixa com número estranho.
 */
export function remainingLabel(expiresAt: string, now: number = Date.now()): string {
  const minutes = Math.max(0, Math.ceil((Date.parse(expiresAt) - now) / 60_000));
  if (minutes >= 60) {
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    return rest === 0 ? `${hours}h` : `${hours}h${String(rest).padStart(2, '0')}`;
  }
  return `${minutes} min`;
}
