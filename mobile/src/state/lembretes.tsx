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
import { AppState } from 'react-native';

import { notificadorExpo, type Notificador, type Permissao } from '../lembretes/notificador';
import { planejarLembretes, type Preferencias } from '../lembretes/plano';
import { preferenciasStore, type PreferenciasStore } from '../lembretes/preferencias';
import { useApi } from './api';

interface Lembretes {
  /** `null` enquanto o aparelho não respondeu. */
  readonly preferencias: Preferencias | null;
  readonly permissao: Permissao | null;
  /** Depois de registrar drill ou sessão: pede a permissão na primeira vez e reagenda. */
  readonly aposRegistro: () => void;
  readonly salvar: (preferencias: Preferencias) => Promise<void>;
  readonly pedirPermissao: () => Promise<void>;
}

const LembretesContext = createContext<Lembretes | null>(null);

// Fora do componente: um padrão recriado a cada render refaria o reagendamento a cada render.
const relogio = () => new Date();

/**
 * O lembrete de revisão vencida (#142), agendado no aparelho a partir da agenda do SRS.
 *
 * Mora dentro dos portões, então só existe com conta: ao sair, ser bloqueado ou excluir a conta, o
 * provedor desmonta e cancela o que estava agendado — lembrete de quem saiu seria de outra pessoa
 * no mesmo aparelho.
 *
 * - **Reagenda ao abrir o app e ao voltar para ele**, e após cada registro. O plano sai da árvore,
 *   que traz o `nextReviewOn` de cada nó — a mesma data de que a agenda do backend sai.
 * - **O pedido de permissão é em contexto**, depois do primeiro registro, nunca na abertura: pedido
 *   sem motivo aparente é o que a pessoa nega.
 * - **Nada aqui pode quebrar tela.** Falha da API ou do nativo é lembrete perdido, como evento de
 *   uso perdido na D50; o reagendamento seguinte refaz o plano.
 */
export function LembretesProvider({
  children,
  notificador = notificadorExpo,
  preferenciasGuardadas = preferenciasStore,
  agora = relogio,
}: {
  children: ReactNode;
  notificador?: Notificador;
  preferenciasGuardadas?: PreferenciasStore;
  agora?: () => Date;
}) {
  const api = useApi();
  const [preferencias, setPreferencias] = useState<Preferencias | null>(null);
  const [permissao, setPermissao] = useState<Permissao | null>(null);
  // Um reagendamento por vez: dois em paralelo cancelariam e agendariam cada um o seu plano.
  const fila = useRef<Promise<void>>(Promise.resolve());
  const desmontado = useRef(false);

  const reagendar = useCallback((): Promise<void> => {
    fila.current = fila.current.then(async () => {
      try {
        const prefs = await preferenciasGuardadas.get();
        const perm = await notificador.permissao();
        if (desmontado.current) return;
        setPreferencias(prefs);
        setPermissao(perm);
        if (!prefs.ligado || perm !== 'concedida') {
          await notificador.cancelarTodos();
          return;
        }
        // A árvore vem antes de cancelar: sem rede, o plano anterior continua de pé.
        const arvore = await api.getTree();
        const datas = (arvore.modules ?? []).flatMap((modulo) =>
          (modulo.nodes ?? []).map((no) => no.nextReviewOn),
        );
        if (desmontado.current) return;
        await notificador.cancelarTodos();
        for (const lembrete of planejarLembretes(datas, agora(), prefs)) {
          await notificador.agendar(lembrete);
        }
      } catch {
        // Lembrete perdido; o próximo reagendamento refaz o plano.
      }
    });
    return fila.current;
  }, [api, notificador, preferenciasGuardadas, agora]);

  useEffect(() => {
    desmontado.current = false;
    void reagendar();
    const assinatura = AppState.addEventListener('change', (estado) => {
      if (estado === 'active') void reagendar();
    });
    return () => {
      assinatura.remove();
      desmontado.current = true;
      fila.current = fila.current.then(() => notificador.cancelarTodos()).catch(() => undefined);
    };
  }, [reagendar, notificador]);

  const valor = useMemo<Lembretes>(
    () => ({
      preferencias,
      permissao,
      aposRegistro() {
        void (async () => {
          try {
            if ((await notificador.permissao()) === 'indefinida') {
              setPermissao(await notificador.pedirPermissao());
            }
          } catch {
            // segue para reagendar com o que houver
          }
          await reagendar();
        })();
      },
      async salvar(novas) {
        setPreferencias(novas);
        await preferenciasGuardadas.set(novas);
        await reagendar();
      },
      async pedirPermissao() {
        setPermissao(await notificador.pedirPermissao());
        await reagendar();
      },
    }),
    [preferencias, permissao, notificador, preferenciasGuardadas, reagendar],
  );

  return <LembretesContext.Provider value={valor}>{children}</LembretesContext.Provider>;
}

export function useLembretes(): Lembretes {
  const lembretes = useContext(LembretesContext);
  if (!lembretes) throw new Error('useLembretes fora do LembretesProvider');
  return lembretes;
}
