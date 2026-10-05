/**
 * Substitutos de módulo dos testes do app.
 *
 * - `expo-router`: as telas usam `Link`, `useRouter` e `Stack.Screen`, que exigem o navegador
 *   montado. Aqui viram peças inertes, e `router` fica exposto para o teste conferir a navegação.
 * - `react-native-youtube-iframe`: o player é uma WebView, que não existe no Jest. O que se testa é
 *   o crédito ao canal, não o player.
 * - `expo-web-browser`: abrir o site é efeito externo.
 */
// Prefixo `mock`: é o único nome de fora que o Jest deixa a fábrica do `jest.mock` enxergar.
const mockRouter = { push: jest.fn(), replace: jest.fn(), back: jest.fn() };

jest.mock('expo-router', () => {
  const React = require('react');
  const { Text } = require('react-native');
  return {
    router: mockRouter,
    useRouter: () => mockRouter,
    useLocalSearchParams: () => ({}),
    useFocusEffect: () => undefined,
    Link: ({ children, asChild }) =>
      asChild ? children : React.createElement(Text, null, children),
    Stack: { Screen: () => null },
  };
});

jest.mock('react-native-youtube-iframe', () => () => null);

// Recuos da área segura sem o provedor nativo: o próprio pacote traz o substituto dos testes.
jest.mock(
  'react-native-safe-area-context',
  () => require('react-native-safe-area-context/jest/mock').default,
);

jest.mock('expo-web-browser', () => ({ openBrowserAsync: jest.fn() }));

beforeEach(() => {
  mockRouter.push.mockClear();
  mockRouter.replace.mockClear();
  mockRouter.back.mockClear();
});
