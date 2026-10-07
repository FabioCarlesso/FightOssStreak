import { useCallback, useEffect, useState } from 'react';

export interface AsyncState<T> {
  readonly data: T | null;
  readonly loading: boolean;
  readonly error: Error | null;
  readonly reload: () => void;
}

/**
 * O mesmo carregador do web (`web/src/state/useAsync.ts`): estados explícitos e dados anteriores
 * **preservados** durante o recarregamento, para o resultado do quiz não sumir no `reload()`.
 */
export function useAsync<T>(
  loader: () => Promise<T>,
  deps: readonly unknown[] = [],
): AsyncState<T> {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const reload = useCallback(() => setReloadToken((token) => token + 1), []);

  // Recarga nova volta a "carregando" e limpa o erro ainda na renderização, não no efeito: assim a
  // tela nunca pinta um quadro com as dependências novas e o estado da carga anterior. É o padrão
  // de ajustar estado quando uma entrada muda (react.dev/learn/you-might-not-need-an-effect).
  const chave = [...deps, reloadToken];
  const [chaveAnterior, setChaveAnterior] = useState(chave);
  if (
    chave.length !== chaveAnterior.length ||
    chave.some((v, i) => !Object.is(v, chaveAnterior[i]))
  ) {
    setChaveAnterior(chave);
    setLoading(true);
    setError(null);
  }

  useEffect(() => {
    let cancelled = false;

    loader()
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((cause: unknown) => {
        if (!cancelled) setError(cause instanceof Error ? cause : new Error(String(cause)));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, reloadToken]);

  return { data, loading, error, reload };
}
