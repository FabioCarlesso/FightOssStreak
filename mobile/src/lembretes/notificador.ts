import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';

import type { Lembrete } from './plano';

/**
 * A ponte com o `expo-notifications`: só notificação **local**, agendada no próprio aparelho. Não há
 * servidor de push, token de dispositivo nem terceiro novo (#142, D68).
 *
 * É uma interface para os testes trocarem o nativo por um falso, como a API (`useApi`).
 */
export type Permissao = 'concedida' | 'negada' | 'indefinida';

export interface Notificador {
  readonly permissao: () => Promise<Permissao>;
  /** O pedido do sistema. Só faz sentido com a permissão `indefinida`. */
  readonly pedirPermissao: () => Promise<Permissao>;
  /** O app não agenda outra notificação, então cancelar todas é cancelar os lembretes. */
  readonly cancelarTodos: () => Promise<void>;
  readonly agendar: (lembrete: Lembrete) => Promise<void>;
}

const CANAL = 'revisao';

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
function traduzir(resposta: Notifications.NotificationPermissionsStatus): Permissao {
  if (resposta.granted) return 'concedida';
  return resposta.status === Notifications.PermissionStatus.UNDETERMINED ? 'indefinida' : 'negada';
}

export const notificadorExpo: Notificador = {
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
    if (Platform.OS === 'android') {
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
