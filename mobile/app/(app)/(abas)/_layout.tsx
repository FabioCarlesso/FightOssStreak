import { Tabs } from 'expo-router';
import { Text } from 'react-native';

import { cores } from '../../../src/theme';

/** As quatro abas do MVP mobile. Admin, painel e feedback continuam só no web (#141). */
export default function AbasLayout() {
  const icone = (emoji: string) =>
    function Icone() {
      return <Text style={{ fontSize: 18 }}>{emoji}</Text>;
    };

  return (
    <Tabs
      screenOptions={{
        headerStyle: { backgroundColor: cores.cartao },
        headerTintColor: cores.texto,
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
