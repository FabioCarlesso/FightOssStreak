import type { AccountView } from '@fos/types';
import { createContext, useContext } from 'react';

export interface AccountSession {
  /** Conta autenticada e já liberada — abaixo do `AuthGate` não existe outro estado. */
  readonly account: AccountView;
  /**
   * Reconsulta `GET /api/me`.
   *
   * É por aqui que sair e excluir a conta viram tela nova: as duas ações derrubam a sessão no
   * servidor, e a releitura devolve 401, que o portão traduz em tela de login. Sem isso a UI teria
   * que adivinhar para onde ir depois de cada ação.
   */
  readonly reload: () => void;
}

/**
 * Conta da sessão atual (#24).
 *
 * O provider vive em `AuthGate.tsx`: arquivo que exporta componente não pode exportar hook junto
 * sem quebrar o fast refresh (e o lint) — mesmo arranjo do `DemoModeContext`.
 */
export const AccountContext = createContext<AccountSession | null>(null);

export function useAccount(): AccountSession {
  const session = useContext(AccountContext);
  if (!session) {
    throw new Error('useAccount precisa de um <AuthGate> acima na árvore');
  }
  return session;
}

/**
 * Versão que aceita não haver sessão.
 *
 * Existe para componentes que também são renderizados fora do portão — hoje o `DisclaimerGate`,
 * que é testado sozinho e não pode exigir um `AuthGate` acima só para renderizar.
 */
export function useAccountOrNull(): AccountSession | null {
  return useContext(AccountContext);
}
