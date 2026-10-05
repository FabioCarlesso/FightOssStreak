import { previewIntervalDays, RECALL_LABELS, todayIso, type Recall } from '@fos/domain';
import type { DrillResult, SrsView } from '@fos/types';
import { useState } from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';

import { useApi } from '../state/api';
import { cores, estilos } from '../theme';
import { Botao, Opcao } from './ui';

/**
 * "Treinei isso hoje" — o drill avulso, registrado pela tela do nó. A auto-avaliação alimenta o
 * SM-2, e a prévia de "volta em N dias" sai de `@fos/domain`, a mesma do web.
 */
export function Drill({
  codigo,
  srs,
  onRegistrado,
}: {
  codigo: string;
  srs?: SrsView;
  onRegistrado: () => void;
}) {
  const api = useApi();
  const [recall, setRecall] = useState<Recall>('OK');
  const [nota, setNota] = useState('');
  const [salvando, setSalvando] = useState(false);
  const [resultado, setResultado] = useState<DrillResult | null>(null);
  const [falha, setFalha] = useState<string | null>(null);

  const previa = previewIntervalDays(srs, recall, todayIso());

  async function registrar() {
    setSalvando(true);
    setFalha(null);
    try {
      setResultado(await api.logDrill(codigo, { recall, note: nota.trim() || undefined }));
      setNota('');
      onRegistrado();
    } catch (causa) {
      setFalha(causa instanceof Error ? causa.message : String(causa));
    } finally {
      setSalvando(false);
    }
  }

  return (
    <View style={{ gap: 8 }}>
      <Text style={estilos.dica}>Como saiu no treino de hoje?</Text>
      <View style={styles.opcoes}>
        {RECALL_LABELS.map((opcao) => (
          <Opcao
            key={opcao.value}
            rotulo={opcao.label}
            dica={opcao.hint}
            selecionada={recall === opcao.value}
            onPress={() => setRecall(opcao.value)}
          />
        ))}
      </View>
      <TextInput
        accessibilityLabel="Anotação do drill"
        placeholder="Anotação (opcional): o que travou, o que o professor corrigiu…"
        placeholderTextColor={cores.dica}
        multiline
        maxLength={1000}
        style={styles.campo}
        value={nota}
        onChangeText={setNota}
      />
      {previa ? (
        <Text style={estilos.dica}>Se registrar assim, volta em {previa} dias.</Text>
      ) : null}
      {falha ? <Text style={estilos.erro}>{falha}</Text> : null}
      <Botao
        titulo={salvando ? 'Registrando…' : 'Treinei isso hoje'}
        desabilitado={salvando}
        onPress={() => void registrar()}
      />
      {resultado ? (
        <Text style={estilos.corpo} testID="drill-resultado">
          Registrado. Próxima revisão em {resultado.nextReviewOn} · streak de{' '}
          {resultado.streak?.currentStreak} dia(s).
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  opcoes: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  campo: {
    borderWidth: 1,
    borderColor: cores.borda,
    borderRadius: 10,
    padding: 10,
    color: cores.texto,
    minHeight: 60,
    textAlignVertical: 'top',
  },
});
