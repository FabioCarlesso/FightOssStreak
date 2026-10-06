import { criarNotificador, notificadorIndisponivel } from './notificador';

interface Resposta {
  granted: boolean;
  status: 'granted' | 'denied' | 'undetermined';
  canAskAgain: boolean;
}

const CONCEDIDA: Resposta = { granted: true, status: 'granted', canAskAgain: true };
/** O Android 13+ antes de qualquer pedido: `denied`, mas ainda dá para perguntar. */
const NUNCA_PERGUNTADA_ANDROID: Resposta = { granted: false, status: 'denied', canAskAgain: true };
const NUNCA_PERGUNTADA_IOS: Resposta = {
  granted: false,
  status: 'undetermined',
  canAskAgain: true,
};
const BLOQUEADA: Resposta = { granted: false, status: 'denied', canAskAgain: false };

/** O `expo-notifications` em miniatura: só o que o notificador usa. */
function moduloFalso(atual: Resposta, aoPedir: Resposta = CONCEDIDA) {
  return {
    setNotificationHandler: jest.fn(),
    getPermissionsAsync: jest.fn().mockResolvedValue(atual),
    requestPermissionsAsync: jest.fn().mockResolvedValue(aoPedir),
    cancelAllScheduledNotificationsAsync: jest.fn().mockResolvedValue(undefined),
    scheduleNotificationAsync: jest.fn().mockResolvedValue('id'),
    setNotificationChannelAsync: jest.fn().mockResolvedValue(null),
    AndroidImportance: { DEFAULT: 3 },
    PermissionStatus: { GRANTED: 'granted', DENIED: 'denied', UNDETERMINED: 'undetermined' },
    SchedulableTriggerInputTypes: { DATE: 'date' },
  };
}

type Carregar = NonNullable<Parameters<typeof criarNotificador>[0]>['carregar'];

function marcaFalsa(jaPediu = false) {
  let pediu = jaPediu;
  return {
    jaPediu: jest.fn(() => Promise.resolve(pediu)),
    marcar: jest.fn(() => {
      pediu = true;
      return Promise.resolve();
    }),
  };
}

function notificadorCom(modulo: ReturnType<typeof moduloFalso>, marca = marcaFalsa()) {
  return criarNotificador({
    expoGo: false,
    plataforma: 'android',
    carregar: (() => modulo) as unknown as Carregar,
    marca,
  });
}

describe('criarNotificador', () => {
  it('no Expo Go do Android nem tenta carregar o módulo, porque o import lança erro', async () => {
    const carregar = jest.fn();
    const notificador = criarNotificador({ expoGo: true, plataforma: 'android', carregar });

    expect(notificador).toBe(notificadorIndisponivel);
    expect(carregar).not.toHaveBeenCalled();
    expect(await notificador.permissao()).toBe('indisponivel');
  });

  it('se o módulo lançar erro ao carregar, cai no notificador inerte em vez de derrubar o app', () => {
    const carregar = jest.fn(() => {
      throw new Error('módulo nativo ausente');
    });

    expect(criarNotificador({ expoGo: false, plataforma: 'ios', carregar })).toBe(
      notificadorIndisponivel,
    );
  });

  it('o Expo Go do iOS carrega o módulo: lá o push só avisa, e a notificação local funciona', () => {
    const carregar = jest.fn(() => moduloFalso(CONCEDIDA));
    criarNotificador({
      expoGo: true,
      plataforma: 'ios',
      carregar: carregar as unknown as Carregar,
    });

    expect(carregar).toHaveBeenCalled();
  });

  it.each([
    ['concedida', CONCEDIDA, 'concedida'],
    [
      'nunca perguntada no Android 13+ (denied, mas pode perguntar)',
      NUNCA_PERGUNTADA_ANDROID,
      'indefinida',
    ],
    ['nunca perguntada no iOS', NUNCA_PERGUNTADA_IOS, 'indefinida'],
    ['bloqueada pelo sistema', BLOQUEADA, 'negada'],
  ] as const)('permissão %s vira %s', async (_caso, resposta, esperado) => {
    expect(await notificadorCom(moduloFalso(resposta)).permissao()).toBe(esperado);
  });

  it('depois que o app pediu uma vez, uma recusa vale como negada mesmo podendo perguntar de novo', async () => {
    const marca = marcaFalsa();
    const notificador = notificadorCom(
      moduloFalso(NUNCA_PERGUNTADA_ANDROID, NUNCA_PERGUNTADA_ANDROID),
      marca,
    );

    expect(await notificador.permissao()).toBe('indefinida');
    expect(await notificador.pedirPermissao()).toBe('negada');
    expect(marca.marcar).toHaveBeenCalled();
    expect(await notificador.permissao()).toBe('negada');
  });

  it('no Android, agenda no canal próprio com gatilho de data', async () => {
    const modulo = moduloFalso(CONCEDIDA);
    const notificador = notificadorCom(modulo);
    const quando = new Date(2026, 9, 6, 19, 0);

    await notificador.agendar({ quando, quantidade: 2, titulo: 'Hora de revisar', corpo: 'x' });

    expect(modulo.setNotificationChannelAsync).toHaveBeenCalledWith(
      'revisao',
      expect.objectContaining({ name: 'Revisões vencidas' }),
    );
    expect(modulo.scheduleNotificationAsync).toHaveBeenCalledWith({
      content: { title: 'Hora de revisar', body: 'x' },
      trigger: { type: 'date', date: quando, channelId: 'revisao' },
    });
  });
});
