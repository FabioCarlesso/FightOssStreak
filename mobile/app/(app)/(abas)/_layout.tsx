import { Tabs } from 'expo-router';
import { Text } from 'react-native';

import { AtalhoDeTema } from '../../../src/components/AtalhoDeTema';
import { useTema } from '../../../src/state/tema';

/** As quatro abas do MVP mobile. Admin, painel e feedback continuam só no web (#141). */
export default function AbasLayout() {
  const { cores } = useTema();
  const icone = (emoji: string) =>
    function Icone() {
      return <Text style={{ fontSize: 18 }}>{emoji}</Text>;
    };

  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: cores.cartao },
        headerTintColor: cores.texto,
        // Troca claro/escuro com um toque, em qualquer aba (D74).
        headerRight: () => <AtalhoDeTema />,
        tabBarStyle: { backgroundColor: cores.cartao, borderTopColor: cores.borda },
        tabBarActiveTintColor: cores.destaque,
        tabBarInactiveTintColor: cores.textoFraco,
        sceneStyle: { backgroundColor: cores.fundo },
      }}
    >
      <Tabs.Screen name="index" options={{ title: 'Hoje', tabBarIcon: icone('🔥') }} />
      <Tabs.Screen name="arvore" options={{ title: 'Árvore', tabBarIcon: icone('🌳') }} />
      <Tabs.Screen name="diario" options={{ title: 'Diário', tabBarIcon: icone('📓') }} />
      <Tabs.Screen name="conta" options={{ title: 'Conta', tabBarIcon: icone('👤') }} />
    </Tabs>
  );
}
