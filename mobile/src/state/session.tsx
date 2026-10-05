import type { AccountView } from '@fos/types';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

import { ApiError } from '../api/client';
import { useApi } from './api';
import { acessoRecusado, semSessao } from './avisos';
import { tokenStore, type TokenStore } from './token';

export type Fase =
  | { readonly tipo: 'carregando' }
  | { readonly tipo: 'sem-token' }
  | { readonly tipo: 'erro'; readonly erro: Error }
  | { readonly tipo: 'conta'; readonly conta: AccountView };

interface Sessao {
  readonly fase: Fase;
  readonly entrar: (email: string, senha: string) => Promise<void>;
  readonly sair: () => Promise<void>;
  readonly contaExcluida: () => Promise<void>;
  readonly recarregar: () => void;
}

const SessaoContext = createContext<Sessao | null>(null);

/**
 * Quem está no app, decidido pelo token (D68) — o equivalente do `AuthGate` do web.
 *
 * Sem token, a fase é `sem-token` e o portão mostra o login. Com token, a conta vem de `/api/me`, e
 * um 401 ali (ou em qualquer chamada, pelo aviso `semSessao`) apaga o token guardado: token morto no
 * aparelho só serviria para responder 401 de novo em toda tela.
 */
export function SessionProvider({
  children,
  tokens = tokenStore,
}: {
  children: ReactNode;
  tokens?: TokenStore;
}) {
  const api = useApi();
  const [fase, setFase] = useState<Fase>({ tipo: 'carregando' });
  const [versao, setVersao] = useState(0);

  const recarregar = useCallback(() => setVersao((v) => v + 1), []);

  const esquecerToken = useCallback(async () => {
    await tokens.clear();
    setFase({ tipo: 'sem-token' });
  }, [tokens]);

  useEffect(() => {
    let vivo = true;
    void (async () => {
      const token = await tokens.get();
      if (!token) {
        if (vivo) setFase({ tipo: 'sem-token' });
        return;
      }
      try {
        const conta = await api.getAccount();
        if (vivo) setFase({ tipo: 'conta', conta });
      } catch (causa) {
        if (!vivo) return;
        if (causa instanceof ApiError && causa.isUnauthenticated) {
          await esquecerToken();
        } else {
          setFase({
            tipo: 'erro',
            erro: causa instanceof Error ? causa : new Error(String(causa)),
          });
        }
      }
    })();
    return () => {
      vivo = false;
    };
  }, [api, tokens, esquecerToken, versao]);

  useEffect(() => semSessao.assinar(() => void esquecerToken()), [esquecerToken]);
  useEffect(() => acessoRecusado.assinar(recarregar), [recarregar]);

  const valor = useMemo<Sessao>(
    () => ({
      fase,
      recarregar,
      async entrar(email, senha) {
        const { token } = await api.mobileLoginWithPassword(email.trim(), senha);
        if (!token) throw new Error('A API não devolveu o token.');
        await tokens.set(token);
        setFase({ tipo: 'carregando' });
        recarregar();
      },
      async sair() {
        // Melhor esforço: sem rede, o token não é revogado no servidor, mas sai do aparelho de
        // qualquer jeito — "sair" que deixa a pessoa dentro seria pior.
        try {
          await api.mobileLogout();
        } catch {
          // segue para apagar o token local
        }
        await esquecerToken();
      },
      async contaExcluida() {
        await esquecerToken();
      },
    }),
    [api, fase, recarregar, esquecerToken, tokens],
  );

  return <SessaoContext.Provider value={valor}>{children}</SessaoContext.Provider>;
}

export function useSessao(): Sessao {
  const sessao = useContext(SessaoContext);
  if (!sessao) throw new Error('useSessao fora do SessionProvider');
  return sessao;
}

/** A conta, para as telas que só existem depois do portão. */
export function useConta(): AccountView {
  const { fase } = useSessao();
  if (fase.tipo !== 'conta') throw new Error('useConta antes do portão de conta');
  return fase.conta;
}
