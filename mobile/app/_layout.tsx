import { Slot } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { createAppApi } from '../src/api/client';
import { ApiProvider } from '../src/state/api';
import { SessionProvider } from '../src/state/session';

const api = createAppApi();

/**
 * Raiz do app: só provedores. Os portões (versão, conta, aviso) ficam no layout do grupo `(app)`,
 * para a raiz sempre montar o navegador e o roteador estar pronto desde o primeiro quadro.
 */
export default function RaizLayout() {
  return (
    <SafeAreaProvider>
      <ApiProvider api={api}>
        <SessionProvider>
          <Slot />
        </SessionProvider>
      </ApiProvider>
      <StatusBar style="light" />
    </SafeAreaProvider>
  );
}
