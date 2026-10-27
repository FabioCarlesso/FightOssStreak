import * as SecureStore from 'expo-secure-store';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useColorScheme } from 'react-native';

import {
  estilosBase,
  estilosPorTema,
  paletas,
  type Cores,
  type Estilos,
  type NomeDoTema,
} from '../theme';

/**
 * O tema do app (D74, #187): segue o modo do aparelho (`useColorScheme`) por padrão, e a tela
 * Conta deixa escolher escuro ou claro à mão. Quando o aparelho não diz nada, vale o escuro.
 *
 * A escolha fica **só no aparelho**, no `expo-secure-store` que o app já usa para o token e o
 * lembrete (#142) — não é dado de conta, não vai para a API e a coleta de uso (D50) não a vê.
 */

export type PreferenciaDeTema = 'sistema' | 'escuro' | 'claro';

export const OPCOES_DE_TEMA: ReadonlyArray<{ valor: PreferenciaDeTema; rotulo: string }> = [
  { valor: 'sistema', rotulo: 'Sistema' },
  { valor: 'escuro', rotulo: 'Escuro' },
  { valor: 'claro', rotulo: 'Claro' },
];

export function resolverTema(
  preferencia: PreferenciaDeTema,
  sistema: string | null | undefined,
): NomeDoTema {
  if (preferencia !== 'sistema') return preferencia;
  return sistema === 'light' ? 'claro' : 'escuro';
}

const CHAVE = 'fos.tema';

export interface PreferenciaDeTemaStore {
  readonly get: () => Promise<PreferenciaDeTema>;
  readonly set: (preferencia: PreferenciaDeTema) => Promise<void>;
}

export const preferenciaDeTemaStore: PreferenciaDeTemaStore = {
  async get() {
    try {
      const salvo = await SecureStore.getItemAsync(CHAVE);
      return salvo === 'escuro' || salvo === 'claro' ? salvo : 'sistema';
    } catch {
      return 'sistema';
    }
  },
  async set(preferencia) {
    if (preferencia === 'sistema') await SecureStore.deleteItemAsync(CHAVE);
    else await SecureStore.setItemAsync(CHAVE, preferencia);
  },
};

interface Escolha {
  readonly preferencia: PreferenciaDeTema;
  readonly escolher: (preferencia: PreferenciaDeTema) => void;
}

const TemaContext = createContext<Escolha | null>(null);

/**
 * Guarda a escolha manual. Mora na raiz, acima do login: a tela de entrada também tem tema.
 *
 * Até o aparelho responder, vale o sistema — uma leitura local, que termina antes de a pessoa
 * perceber na prática. Falha ao gravar só faz a escolha não sobreviver ao próximo acesso.
 */
export function TemaProvider({
  children,
  guardada = preferenciaDeTemaStore,
}: {
  children: ReactNode;
  guardada?: PreferenciaDeTemaStore;
}) {
  const [preferencia, setPreferencia] = useState<PreferenciaDeTema>('sistema');
  // Se a pessoa escolher antes de o aparelho responder, a escolha dela vence a guardada.
  const escolheu = useRef(false);

  useEffect(() => {
    let vivo = true;
    void guardada.get().then((salva) => {
      if (vivo && !escolheu.current) setPreferencia(salva);
    });
    return () => {
      vivo = false;
    };
  }, [guardada]);

  const escolher = useCallback(
    (nova: PreferenciaDeTema) => {
      escolheu.current = true;
      setPreferencia(nova);
      guardada.set(nova).catch(() => undefined);
    },
    [guardada],
  );

  const valor = useMemo(() => ({ preferencia, escolher }), [preferencia, escolher]);
  return <TemaContext.Provider value={valor}>{children}</TemaContext.Provider>;
}

export interface Tema {
  readonly nome: NomeDoTema;
  readonly cores: Cores;
  readonly estilos: Estilos;
  readonly preferencia: PreferenciaDeTema;
  readonly escolher: (preferencia: PreferenciaDeTema) => void;
}

const nada = () => undefined;

/**
 * O tema em uso. Sem `TemaProvider` em volta (um teste que monta só a tela), segue o sistema e
 * não guarda escolha.
 */
export function useTema(): Tema {
  const sistema = useColorScheme();
  const escolha = useContext(TemaContext);
  const preferencia = escolha?.preferencia ?? 'sistema';
  const escolher = escolha?.escolher ?? nada;
  const nome = resolverTema(preferencia, sistema);
  return useMemo(
    () => ({ nome, cores: paletas[nome], estilos: estilosBase(nome), preferencia, escolher }),
    [nome, preferencia, escolher],
  );
}

/**
 * Estilos próprios de um componente, por tema. Fica no módulo, fora do componente:
 *
 *     const useEstilos = criarEstilos((cores) => StyleSheet.create({ ... }));
 *     function Peca() { const styles = useEstilos(); ... }
 */
export function criarEstilos<T>(fabrica: (cores: Cores) => T): () => T {
  const porTema = estilosPorTema(fabrica);
  return function useEstilos() {
    return porTema(useTema().nome);
  };
}
