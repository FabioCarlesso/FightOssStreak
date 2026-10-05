import {
  feelingLabel,
  formatDay,
  monthRange,
  recallLabel,
  sessionKindLabel,
  todayIso,
} from '@fos/domain';
import type { DiaryDay, TrainingSession } from '@fos/types';
import { useRouter } from 'expo-router';
import { useMemo } from 'react';
import { StyleSheet, Text, View } from 'react-native';

import { Botao, Cartao, Carregando, FalhaDaApi, Pilula, Tela } from '../components/ui';
import { useApi } from '../state/api';
import { useAoVoltar } from '../state/useAoVoltar';
import { useAsync } from '../state/useAsync';
import { cores, estilos } from '../theme';

/**
 * O diário do mês corrente (#114, D56): um bloco por dia, com as sessões e os drills avulsos.
 *
 * Peso e sensação aparecem como a pessoa os anotou, sem meta, faixa nem comparação (D57). Filtros e
 * edição de sessão ficam no web por enquanto.
 */
export function TelaDiario() {
  const api = useApi();
  const router = useRouter();
  const mes = useMemo(() => monthRange(todayIso()), []);
  const diario = useAsync(() => api.getDiary(mes), [mes.de, mes.ate]);
  useAoVoltar(diario.reload);

  if (diario.loading && !diario.data) return <Carregando texto="Carregando diário…" />;
  if (diario.error && !diario.data)
    return <FalhaDaApi erro={diario.error} onRetry={diario.reload} />;

  const dias = (diario.data?.days ?? []).filter(
    (dia) => (dia.sessions?.length ?? 0) > 0 || (dia.avulsos?.length ?? 0) > 0,
  );

  return (
    <Tela>
      <Cartao>
        <Text style={estilos.subtitulo}>Diário deste mês</Text>
        <Text style={estilos.dica} testID="contagem-mes">
          {diario.data?.sessionsInMonth ?? 0}{' '}
          {diario.data?.sessionsInMonth === 1 ? 'treino registrado' : 'treinos registrados'} neste
          mês. Dia de descanso também cabe aqui, e é o único que não conta no streak.
        </Text>
        <Botao titulo="Registrar treino" onPress={() => router.push('/diario/nova')} />
      </Cartao>

      {dias.length === 0 ? (
        <Text style={estilos.dica}>Nada registrado neste mês. A data já basta para começar.</Text>
      ) : null}

      {dias.map((dia) => (
        <BlocoDoDia key={dia.day} dia={dia} />
      ))}
    </Tela>
  );
}

function BlocoDoDia({ dia }: { dia: DiaryDay }) {
  return (
    <Cartao>
      <View style={styles.cabecalho}>
        <Text style={estilos.subtitulo}>{formatDay(dia.day)}</Text>
        {!dia.countsAsTrainingDay ? <Pilula texto="Não conta no streak" /> : null}
      </View>
      {dia.sessions?.map((sessao) => (
        <LinhaDaSessao key={sessao.id} sessao={sessao} />
      ))}
      {(dia.avulsos?.length ?? 0) > 0 ? (
        <View style={{ gap: 4 }}>
          <Text style={estilos.dica}>Registrado direto no nó</Text>
          {dia.avulsos?.map((t, i) => (
            <Text key={`${t.nodeCode}-${i}`} style={estilos.corpo}>
              <Text style={estilos.codigo}>{t.nodeCode}</Text> {t.nodeTitle} ·{' '}
              {recallLabel(t.recall)}
            </Text>
          ))}
        </View>
      ) : null}
    </Cartao>
  );
}

function LinhaDaSessao({ sessao }: { sessao: TrainingSession }) {
  return (
    <View style={styles.sessao}>
      <View style={styles.pilulas}>
        <Text style={[estilos.corpo, { fontWeight: '600' }]}>{sessionKindLabel(sessao.kind)}</Text>
        {sessao.durationMinutes != null ? <Pilula texto={`${sessao.durationMinutes} min`} /> : null}
        {sessao.feeling ? <Pilula texto={feelingLabel(sessao.feeling)} /> : null}
        {/* O número que a pessoa escreveu, sem meta nem comparação (D57). */}
        {sessao.weightKg != null ? <Pilula texto={`${sessao.weightKg} kg`} /> : null}
      </View>
      {sessao.learned ? <Text style={estilos.corpo}>{sessao.learned}</Text> : null}
      {sessao.improve ? <Text style={estilos.dica}>{sessao.improve}</Text> : null}
      {sessao.tecnicas?.map((t, i) => (
        <Text key={`${t.nodeCode}-${i}`} style={estilos.dica}>
          <Text style={estilos.codigo}>{t.nodeCode}</Text> {t.nodeTitle} · {recallLabel(t.recall)}
        </Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  cabecalho: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  sessao: { gap: 4, paddingTop: 8, borderTopWidth: 1, borderTopColor: cores.borda },
  pilulas: { flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: 6 },
});
