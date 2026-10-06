/**
 * Regressão da revisão da #160: no Expo Go do Android, o próprio `import` do `expo-notifications`
 * lança erro, e um import no topo do notificador derrubava o layout `(app)` inteiro antes do login.
 *
 * Arquivo próprio, e sem import nenhum antes do `doMock`, de propósito: se o notificador já tiver
 * sido carregado neste arquivo, o Jest reaproveita o módulo do `jest.setup.js` e o teste passa até
 * com o import de volta no topo — foi o que aconteceu na primeira versão deste teste.
 */
it('carregar o lembrete não carrega o expo-notifications, então o import que lança não derruba o app', () => {
  jest.doMock('expo-notifications', () => {
    throw new Error('removido do Expo Go');
  });

  expect(() => jest.requireActual<object>('../state/lembretes')).not.toThrow();
  expect(() => jest.requireActual<object>('./notificador')).not.toThrow();
});
