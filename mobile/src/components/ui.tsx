import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { cores, estilos } from '../theme';

/** Peças de interface que todas as telas usam: tela rolável, cartão, botão, pílula e estados. */

/**
 * Tela rolável. `segura` é para as telas de portão (login, aviso, bloqueio), que ficam fora do
 * navegador e não têm cabeçalho para afastá-las da barra de status.
 */
export function Tela({ children, segura = false }: { children: ReactNode; segura?: boolean }) {
  const recuos = useSafeAreaInsets();
  return (
    <ScrollView
      style={{ backgroundColor: cores.fundo }}
      contentContainerStyle={[
        estilos.tela,
        segura && { paddingTop: recuos.top + 16, paddingBottom: recuos.bottom + 16 },
      ]}
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </ScrollView>
  );
}

export function Cartao({ children, style }: { children: ReactNode; style?: StyleProp<ViewStyle> }) {
  return <View style={[estilos.cartao, style]}>{children}</View>;
}

export function Botao({
  titulo,
  onPress,
  desabilitado = false,
  variante = 'primario',
}: {
  titulo: string;
  onPress: () => void;
  desabilitado?: boolean;
  variante?: 'primario' | 'secundario' | 'perigo';
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: desabilitado }}
      disabled={desabilitado}
      onPress={onPress}
      style={({ pressed }) => [
        styles.botao,
        variante === 'secundario' && styles.botaoSecundario,
        variante === 'perigo' && styles.botaoPerigo,
        (pressed || desabilitado) && { opacity: desabilitado ? 0.5 : 0.8 },
      ]}
    >
      <Text style={[styles.botaoTexto, variante === 'secundario' && { color: cores.texto }]}>
        {titulo}
      </Text>
    </Pressable>
  );
}

/** Opção selecionável (chip), para tipo de sessão, sensação e auto-avaliação. */
export function Opcao({
  rotulo,
  selecionada,
  onPress,
  dica,
}: {
  rotulo: string;
  selecionada: boolean;
  onPress: () => void;
  dica?: string;
}) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ checked: selecionada }}
      accessibilityLabel={rotulo}
      onPress={onPress}
      style={[styles.opcao, selecionada && styles.opcaoSelecionada]}
    >
      <Text style={[styles.opcaoTexto, selecionada && { color: cores.fundo }]}>{rotulo}</Text>
      {dica ? (
        <Text style={[estilos.dica, selecionada && { color: cores.fundo }]}>{dica}</Text>
      ) : null}
    </Pressable>
  );
}

export function Pilula({ texto, atencao = false }: { texto: string; atencao?: boolean }) {
  return (
    <View style={[styles.pilula, atencao && { borderColor: cores.aviso }]}>
      <Text style={[styles.pilulaTexto, atencao && { color: cores.aviso }]}>{texto}</Text>
    </View>
  );
}

export function Carregando({ texto = 'Carregando…' }: { texto?: string }) {
  return (
    <View style={styles.centro}>
      <ActivityIndicator color={cores.destaque} />
      <Text style={estilos.dica}>{texto}</Text>
    </View>
  );
}

/** Falha de rede ou da API, com o caminho de volta. */
export function FalhaDaApi({ erro, onRetry }: { erro: Error; onRetry: () => void }) {
  return (
    <Tela segura>
      <Cartao>
        <Text style={estilos.subtitulo}>Não foi possível falar com a API</Text>
        <Text style={estilos.erro}>{erro.message}</Text>
        <Botao titulo="Tentar de novo" onPress={onRetry} variante="secundario" />
      </Cartao>
    </Tela>
  );
}

const styles = StyleSheet.create({
  botao: {
    backgroundColor: cores.destaque,
    borderRadius: 10,
    paddingVertical: 12,
    paddingHorizontal: 16,
    alignItems: 'center',
  },
  botaoSecundario: { backgroundColor: 'transparent', borderWidth: 1, borderColor: cores.borda },
  botaoPerigo: { backgroundColor: cores.perigo },
  botaoTexto: { color: '#fff', fontSize: 15, fontWeight: '600' },
  opcao: {
    borderWidth: 1,
    borderColor: cores.borda,
    borderRadius: 10,
    paddingVertical: 8,
    paddingHorizontal: 12,
  },
  opcaoSelecionada: { backgroundColor: cores.destaque, borderColor: cores.destaque },
  opcaoTexto: { color: cores.texto, fontSize: 14, fontWeight: '600' },
  pilula: {
    borderWidth: 1,
    borderColor: cores.borda,
    borderRadius: 999,
    paddingVertical: 2,
    paddingHorizontal: 8,
    alignSelf: 'flex-start',
  },
  pilulaTexto: { color: cores.textoFraco, fontSize: 12 },
  centro: { flex: 1, alignItems: 'center', justifyContent: 'center', gap: 8, padding: 24 },
});
