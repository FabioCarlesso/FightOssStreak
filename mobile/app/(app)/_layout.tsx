import { Stack } from 'expo-router';

import { PortaoAviso, PortaoConta, PortaoVersao } from '../../src/components/Portoes';
import { LembretesProvider } from '../../src/state/lembretes';
import { cores } from '../../src/theme';

/**
 * Tudo que é do app passa pelos portões, na ordem: versão mínima, conta e aviso. O lembrete de
 * revisão (#142) fica depois deles, para só existir com conta e cair junto com ela.
 */
export default function AppLayout() {
  return (
    <PortaoVersao>
      <PortaoConta>
        <PortaoAviso>
          <LembretesProvider>
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
          </LembretesProvider>
        </PortaoAviso>
      </PortaoConta>
    </PortaoVersao>
  );
}
