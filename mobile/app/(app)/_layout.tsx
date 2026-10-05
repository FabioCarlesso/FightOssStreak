import { Stack } from 'expo-router';

import { PortaoAviso, PortaoConta, PortaoVersao } from '../../src/components/Portoes';
import { cores } from '../../src/theme';

/** Tudo que é do app passa pelos portões, na ordem: versão mínima, conta e aviso. */
export default function AppLayout() {
  return (
    <PortaoVersao>
      <PortaoConta>
        <PortaoAviso>
          <Stack
            screenOptions={{
              headerStyle: { backgroundColor: cores.cartao },
              headerTintColor: cores.texto,
              contentStyle: { backgroundColor: cores.fundo },
            }}
          >
            <Stack.Screen name="(abas)" options={{ headerShown: false }} />
            <Stack.Screen name="no/[codigo]" options={{ title: 'Nó' }} />
            <Stack.Screen name="diario/nova" options={{ title: 'Registrar treino' }} />
          </Stack>
        </PortaoAviso>
      </PortaoConta>
    </PortaoVersao>
  );
}
