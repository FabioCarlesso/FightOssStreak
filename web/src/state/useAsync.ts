import { useCallback, useEffect, useState } from 'react';

export interface AsyncState<T> {
  readonly data: T | null;
  readonly loading: boolean;
  readonly error: Error | null;
  readonly reload: () => void;
}

/**
 * Carrega dados assíncronos com estados de carregamento e erro explícitos.
 *
 * Deliberadamente minúsculo: para um app de uso pessoal com meia dúzia de telas, uma biblioteca de
 * data fetching custaria mais em configuração do que resolve.
 *
 * Ponto importante: os dados anteriores são **preservados** durante o recarregamento. Se fossem
 * zerados, a tela cairia no estado de carregamento a cada `reload()` e desmontaria os componentes
 * filhos — que é exatamente o que apagava o resultado do quiz logo depois de respondê-lo.
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
