import { criarNotificador, notificadorIndisponivel } from './notificador';

/** O `expo-notifications` em miniatura: só o que o notificador usa. */
function moduloFalso(status: 'granted' | 'denied' | 'undetermined') {
  return {
    setNotificationHandler: jest.fn(),
    getPermissionsAsync: jest.fn().mockResolvedValue({ granted: status === 'granted', status }),
    requestPermissionsAsync: jest.fn().mockResolvedValue({ granted: true, status: 'granted' }),
    cancelAllScheduledNotificationsAsync: jest.fn().mockResolvedValue(undefined),
    scheduleNotificationAsync: jest.fn().mockResolvedValue('id'),
    setNotificationChannelAsync: jest.fn().mockResolvedValue(null),
    AndroidImportance: { DEFAULT: 3 },
    PermissionStatus: { GRANTED: 'granted', DENIED: 'denied', UNDETERMINED: 'undetermined' },
    SchedulableTriggerInputTypes: { DATE: 'date' },
  };
}

type Carregar = NonNullable<Parameters<typeof criarNotificador>[0]>['carregar'];

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
    const carregar = jest.fn(() => moduloFalso('granted'));
    criarNotificador({
      expoGo: true,
      plataforma: 'ios',
      carregar: carregar as unknown as Carregar,
    });

    expect(carregar).toHaveBeenCalled();
  });

  it.each([
    ['granted', 'concedida'],
    ['undetermined', 'indefinida'],
    ['denied', 'negada'],
  ] as const)('permissão %s do sistema vira %s', async (status, esperado) => {
    const modulo = moduloFalso(status);
    const notificador = criarNotificador({
      expoGo: false,
      plataforma: 'android',
      carregar: (() => modulo) as unknown as Carregar,
    });

    expect(await notificador.permissao()).toBe(esperado);
  });

  it('no Android, agenda no canal próprio com gatilho de data', async () => {
    const modulo = moduloFalso('granted');
    const notificador = criarNotificador({
      expoGo: false,
      plataforma: 'android',
      carregar: (() => modulo) as unknown as Carregar,
    });
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
