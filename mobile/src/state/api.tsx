import { createContext, useContext, type ReactNode } from 'react';

import type { Api } from '../api/client';

/**
 * O cliente da API chega às telas por contexto, e não por import direto: é o que permite aos testes
 * entregar um cliente falso a cada tela sem mexer em módulo nenhum.
 */
const ApiContext = createContext<Api | null>(null);

export function ApiProvider({ api, children }: { api: Api; children: ReactNode }) {
  return <ApiContext.Provider value={api}>{children}</ApiContext.Provider>;
}

export function useApi(): Api {
  const api = useContext(ApiContext);
  if (!api) throw new Error('useApi fora do ApiProvider');
  return api;
}
