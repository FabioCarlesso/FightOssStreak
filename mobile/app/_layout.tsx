import { Slot } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import { createAppApi } from '../src/api/client';
import { ApiProvider } from '../src/state/api';
import { SessionProvider } from '../src/state/session';
import { TemaProvider, useTema } from '../src/state/tema';

const api = createAppApi();

/** Ícones da barra de status claros no tema escuro e escuros no claro (D74). */
function BarraDeStatus() {
  const { nome } = useTema();
  return <StatusBar style={nome === 'claro' ? 'dark' : 'light'} />;
}

/**
 * Raiz do app: só provedores. Os portões (versão, conta, aviso) ficam no layout do grupo `(app)`,
 * para a raiz sempre montar o navegador e o roteador estar pronto desde o primeiro quadro. O tema
 * fica acima de tudo, porque o login também tem tema.
 */
export default function RaizLayout() {
  return (
    <SafeAreaProvider>
      <TemaProvider>
        <ApiProvider api={api}>
          <SessionProvider>
            <Slot />
          </SessionProvider>
        </ApiProvider>
        <BarraDeStatus />
      </TemaProvider>
    </SafeAreaProvider>
  );
}
