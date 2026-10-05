import { useState } from 'react';
import { Text, View } from 'react-native';

import { useApi } from '../state/api';
import { estilos } from '../theme';
import { Botao } from './ui';

/**
 * Exclusão da própria conta (`DELETE /api/me`) — exigência das duas lojas para app com login.
 *
 * Confirmação em dois passos, como no web: o texto diz o que se perde, porque um botão que apaga
 * streak, progresso e agenda não pode ser do tamanho de um "cancelar".
 */
export function ExcluirConta({ onExcluida }: { onExcluida: () => void }) {
  const api = useApi();
  const [confirmando, setConfirmando] = useState(false);
  const [excluindo, setExcluindo] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);

  async function excluir() {
    setExcluindo(true);
    setFalha(null);
    try {
      await api.deleteAccount();
      onExcluida();
    } catch (causa) {
      setFalha(causa instanceof Error ? causa.message : String(causa));
      setExcluindo(false);
    }
  }

  if (!confirmando) {
    return (
      <Botao titulo="Excluir minha conta" variante="perigo" onPress={() => setConfirmando(true)} />
    );
  }

  return (
    <View style={{ gap: 8 }}>
      <Text style={estilos.corpo}>
        Isto apaga a conta e tudo que é dela: progresso na árvore, streak, agenda de revisão,
        diário, anotações e o aceite do aviso. Não dá para desfazer.
      </Text>
      {falha ? <Text style={estilos.erro}>{falha}</Text> : null}
      <Botao
        titulo={excluindo ? 'Excluindo…' : 'Sim, excluir tudo'}
        variante="perigo"
        desabilitado={excluindo}
        onPress={() => void excluir()}
      />
      <Botao
        titulo="Cancelar"
        variante="secundario"
        desabilitado={excluindo}
        onPress={() => setConfirmando(false)}
      />
    </View>
  );
}
