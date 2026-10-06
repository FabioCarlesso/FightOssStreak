import { isRunningInExpoGo } from 'expo';
import { Platform } from 'react-native';

import type { Lembrete } from './plano';

/**
 * A ponte com o `expo-notifications`: só notificação **local**, agendada no próprio aparelho. Não há
 * servidor de push, token de dispositivo nem terceiro novo (#142, D70).
 *
 * É uma interface para os testes trocarem o nativo por um falso, como a API (`useApi`).
 *
 * `indisponivel` é o aparelho em que o módulo não roda — o Expo Go do Android. Ali o lembrete não
 * existe, e a tela diz por quê em vez de oferecer um pedido de permissão que não daria em nada.
 */
export type Permissao = 'concedida' | 'negada' | 'indefinida' | 'indisponivel';

export interface Notificador {
  readonly permissao: () => Promise<Permissao>;
  /** O pedido do sistema. Só faz sentido com a permissão `indefinida`. */
  readonly pedirPermissao: () => Promise<Permissao>;
  /** O app não agenda outra notificação, então cancelar todas é cancelar os lembretes. */
  readonly cancelarTodos: () => Promise<void>;
  readonly agendar: (lembrete: Lembrete) => Promise<void>;
}

type Modulo = typeof import('expo-notifications');

const CANAL = 'revisao';

/** Onde o módulo não roda, nada é agendado e nada quebra. */
export const notificadorIndisponivel: Notificador = {
  permissao: () => Promise.resolve('indisponivel'),
  pedirPermissao: () => Promise.resolve('indisponivel'),
  cancelarTodos: () => Promise.resolve(),
  agendar: () => Promise.resolve(),
};

/**
 * **O `expo-notifications` não pode ser importado no topo do arquivo.** No Expo Go do Android, desde
 * a SDK 53, o próprio import lança erro — o módulo de push foi tirado de lá —, e como este arquivo é
 * carregado pelo layout `(app)`, o app inteiro deixava de abrir antes do login. Foi pego no emulador
 * na revisão da #160; o Jest não pegava porque troca o módulo por um falso, e o `expo export` só
 * monta o bundle, sem executá-lo.
 *
 * Por isso o módulo é carregado aqui, com `require` tardio, só fora do Expo Go do Android e dentro de
 * um `try`: qualquer outro aparelho em que o import falhe também cai no notificador inerte.
 */
export function criarNotificador({
  expoGo = isRunningInExpoGo(),
  plataforma = Platform.OS,
  carregar = carregarModulo,
}: {
  expoGo?: boolean;
  plataforma?: string;
  carregar?: () => Modulo;
} = {}): Notificador {
  if (expoGo && plataforma === 'android') return notificadorIndisponivel;
  try {
    return notificadorExpo(carregar(), plataforma);
  } catch {
    return notificadorIndisponivel;
  }
}

/** O `require` é o ponto: um `import` no topo rodaria na carga do arquivo (ver acima). */
function carregarModulo(): Modulo {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  return require('expo-notifications') as Modulo;
}

function notificadorExpo(Notifications: Modulo, plataforma: string): Notificador {
  let configurado = false;

  /** Com o app aberto, o lembrete aparece como banner, igual a com ele fechado. */
  function configurar(): void {
    if (configurado) return;
    configurado = true;
    Notifications.setNotificationHandler({
      handleNotification: () =>
        Promise.resolve({
          shouldShowBanner: true,
          shouldShowList: true,
          shouldPlaySound: false,
          shouldSetBadge: false,
        }),
    });
  }

  /**
   * Pelo `status`, e não pelo `canAskAgain`: o Android 13 deixa pedir de novo depois da primeira
   * recusa, e quem disse não uma vez não deve ver o pedido a cada registro.
   */
  function traduzir(resposta: Awaited<ReturnType<Modulo['getPermissionsAsync']>>): Permissao {
    if (resposta.granted) return 'concedida';
    return resposta.status === Notifications.PermissionStatus.UNDETERMINED
      ? 'indefinida'
      : 'negada';
  }

  return {
    async permissao() {
      return traduzir(await Notifications.getPermissionsAsync());
    },
    async pedirPermissao() {
      configurar();
      return traduzir(
        await Notifications.requestPermissionsAsync({
          ios: { allowAlert: true, allowSound: true, allowBadge: false },
        }),
      );
    },
    async cancelarTodos() {
      await Notifications.cancelAllScheduledNotificationsAsync();
    },
    async agendar(lembrete) {
      configurar();
      // Android 8+ exige canal. Recriar um canal que já existe só atualiza nome e importância.
      if (plataforma === 'android') {
        await Notifications.setNotificationChannelAsync(CANAL, {
          name: 'Revisões vencidas',
          importance: Notifications.AndroidImportance.DEFAULT,
        });
      }
      await Notifications.scheduleNotificationAsync({
        content: { title: lembrete.titulo, body: lembrete.corpo },
        trigger: {
          type: Notifications.SchedulableTriggerInputTypes.DATE,
          date: lembrete.quando,
          channelId: CANAL,
        },
      });
    },
  };
}
