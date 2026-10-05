import { buildStreakHeatmap, daysBetween, type ActivityDay, type HeatmapCell } from '@fos/domain';
import type { StreakHistory, StreakView } from '@fos/types';
import { useRef } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { cores, estilos } from '../theme';
import { Cartao } from './ui';

/**
 * Streak com contexto, a mesma leitura do `StreakCard` do web: dias ativos nos últimos 30 ao lado
 * da corrente (o critério do MVP), saldo de freeze junto do contador e o heatmap no mesmo cartão.
 *
 * Nenhum número é calculado aqui: corrente, recorde, freeze e dias ativos vêm do backend, e a grade
 * do heatmap sai de `buildStreakHeatmap`, de `@fos/domain` (D59).
 */
export function CartaoStreak({
  streak,
  historico,
}: {
  streak: StreakView;
  historico?: StreakHistory | null;
}) {
  const ativos = streak.activeDaysLast30 ?? 0;
  const meta = streak.targetDaysLast30 ?? 12;
  const progresso = Math.min(100, Math.round((ativos / meta) * 100));
  const freezesNoMes = streak.freezesPerMonth ?? 0;
  const atual = streak.currentStreak ?? 0;

  return (
    <Cartao>
      <View style={styles.linha}>
        <Text style={styles.chama}>🔥</Text>
        <View style={{ flex: 1 }}>
          {/* Número e unidade lado a lado, e não aninhados: o texto pequeno dentro do grande herda a
              altura de linha dele e sai cortado no Android. */}
          <View style={styles.contagemLinha} testID="streak-atual">
            <Text style={styles.contagem}>{atual}</Text>
            <Text style={styles.unidade}>{atual === 1 ? 'dia' : 'dias'} seguidos</Text>
          </View>
          <Text style={estilos.dica}>
            {streak.drilledToday
              ? 'Treino de hoje já registrado.'
              : 'Ainda sem registro hoje — o streak se mantém até o fim do dia.'}
          </Text>
        </View>
      </View>

      <View style={styles.barra} accessibilityLabel={`${ativos} de ${meta} dias na meta`}>
        <View style={[styles.barraCheia, { width: `${progresso}%` }]} />
      </View>
      <Text style={estilos.dica} testID="dias-ativos">
        {ativos} de {meta} dias com treino registrado nos últimos 30
      </Text>
      {(streak.longestStreak ?? 0) > atual ? (
        <Text style={estilos.dica}>Recorde: {streak.longestStreak} dias</Text>
      ) : null}
      {freezesNoMes > 0 ? (
        <Text style={estilos.dica}>
          🧊 {streak.freezesRemaining ?? 0} de {freezesNoMes}{' '}
          {freezesNoMes === 1 ? 'freeze' : 'freezes'} neste mês
          {streak.lastFrozenOn
            ? ` — um cobriu ${curto(streak.lastFrozenOn)} e a sequência seguiu.`
            : '. Um dia perdido é perdoado enquanto houver saldo.'}
        </Text>
      ) : null}

      {historico ? <Heatmap historico={historico} /> : null}
    </Cartao>
  );
}

function Heatmap({ historico }: { historico: StreakHistory }) {
  const rolagem = useRef<ScrollView>(null);
  const { from, to } = historico;
  if (!from || !to) return null;

  const registros: ActivityDay[] = (historico.days ?? [])
    .filter((dia): dia is { day: string; count?: number; frozen?: boolean } => Boolean(dia.day))
    .map((dia) => ({ day: dia.day, count: dia.count ?? 0, frozen: dia.frozen ?? false }));
  const semanas = Math.ceil((daysBetween(from, to) + 1) / 7);
  const mapa = buildStreakHeatmap(registros, to, semanas);

  return (
    <View style={{ gap: 6 }}>
      {/* Abre na borda direita, como no web: a pergunta é "como andaram os últimos tempos". */}
      <ScrollView
        ref={rolagem}
        horizontal
        showsHorizontalScrollIndicator={false}
        onContentSizeChange={() => rolagem.current?.scrollToEnd({ animated: false })}
      >
        <View style={styles.grade} accessibilityLabel={rotulo(mapa)}>
          {mapa.weeks.map((semana, coluna) => (
            <View key={semana[0]?.day ?? coluna} style={styles.semana}>
              {semana.map((celula, linha) => (
                <View
                  key={celula?.day ?? `vazio-${linha}`}
                  style={[styles.celula, estiloDaCelula(celula)]}
                />
              ))}
            </View>
          ))}
        </View>
      </ScrollView>
      <Text style={estilos.dica}>{rotulo(mapa)}.</Text>
    </View>
  );
}

function rotulo(mapa: { activeDays: number; from: string; to: string }): string {
  return (
    `${mapa.activeDays} ${mapa.activeDays === 1 ? 'dia' : 'dias'} com treino registrado` +
    ` entre ${curto(mapa.from)} e ${curto(mapa.to)}`
  );
}

function estiloDaCelula(celula: HeatmapCell | null) {
  // Dia que ainda não chegou não é dia sem treino: fica invisível. Perdoado vem antes da
  // intensidade porque dia coberto tem `level` 0 e se pintaria de falta (D55).
  if (celula === null) return { backgroundColor: 'transparent' };
  if (celula.frozen) return { backgroundColor: cores.gelo };
  return { backgroundColor: cores.calor[celula.level] };
}

/** `YYYY-MM-DD` como dia e mês, sem `Date`: a data é de calendário e fuso não tem o que dizer. */
function curto(dia: string): string {
  const [, mes, d] = dia.split('-');
  return `${d}/${mes}`;
}

const styles = StyleSheet.create({
  linha: { flexDirection: 'row', alignItems: 'center', gap: 12 },
  chama: { fontSize: 32 },
  contagemLinha: { flexDirection: 'row', alignItems: 'baseline', gap: 6 },
  contagem: { color: cores.texto, fontSize: 28, fontWeight: '700' },
  unidade: { color: cores.textoFraco, fontSize: 14 },
  barra: { height: 6, borderRadius: 3, backgroundColor: cores.borda, overflow: 'hidden' },
  barraCheia: { height: 6, backgroundColor: cores.ok },
  grade: { flexDirection: 'row', gap: 3 },
  semana: { gap: 3 },
  celula: { width: 12, height: 12, borderRadius: 2 },
});
