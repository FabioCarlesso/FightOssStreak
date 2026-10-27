import { Pressable, StyleSheet, Text } from 'react-native';

import { criarEstilos, useTema } from '../state/tema';
import { espaco, raio } from '../theme';

/**
 * Atalho do cabeçalho das abas: um toque troca entre claro e escuro (D74). É escolha manual, como
 * no seletor da tela Conta — voltar a seguir o aparelho é lá, em *Sistema*.
 */
export function AtalhoDeTema() {
  const { nome, escolher } = useTema();
  const styles = useEstilos();
  const destino = nome === 'claro' ? 'escuro' : 'claro';
  const rotulo = `Usar tema ${destino}`;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={rotulo}
      hitSlop={espaco.space2}
      onPress={() => escolher(destino)}
      style={({ pressed }) => [styles.atalho, pressed && { opacity: 0.7 }]}
    >
      <Text style={styles.icone}>{destino === 'claro' ? '☀️' : '🌙'}</Text>
    </Pressable>
  );
}

const useEstilos = criarEstilos((cores) =>
  StyleSheet.create({
    atalho: {
      width: 36,
      height: 36,
      marginRight: espaco.space3,
      alignItems: 'center',
      justifyContent: 'center',
      borderWidth: 1,
      borderColor: cores.bordaControle,
      borderRadius: raio.radiusPill,
    },
    icone: { fontSize: 16 },
  }),
);
