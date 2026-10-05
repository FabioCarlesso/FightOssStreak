import { monthRange, todayIso } from '@fos/domain';
import { Link, useRouter } from 'expo-router';
import { useCallback, useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { CartaoStreak } from '../components/CartaoStreak';
import { Botao, Cartao, Carregando, Pilula, Tela } from '../components/ui';
import { useApi } from '../state/api';
import { useAoVoltar } from '../state/useAoVoltar';
import { useAsync } from '../state/useAsync';
import { estilos } from '../theme';

/**
 * Hoje: streak e, principalmente, o que revisar — a mesma tela inicial do web.
 *
 * A agenda vem logo abaixo do streak e antes do diário, como no web (D56c): é o que o produto se
 * propõe a responder. O histórico do heatmap é pedido **depois** do streak, porque `GET /api/streak`
 * é quem grava o dia perdoado (D55); em paralelo, o cartão e a grade discordariam sobre o mesmo dia.
 */
export function TelaHoje() {
  const api = useApi();
  const router = useRouter();
  const streak = useAsync(() => api.getStreak(), []);
  const historico = useAsync(
    () => (streak.data ? api.getStreakHistory() : Promise.resolve(null)),
    [streak.data],
  );
  const agenda = useAsync(() => api.getReviewsToday(), []);
  const mes = useMemo(() => monthRange(todayIso()), []);
  const diario = useAsync(() => api.getDiary({ ...mes, limite: 1 }), [mes.de, mes.ate]);

  // O histórico não entra aqui: ele depende de `streak.data` e é pedido de novo quando o streak muda.
  const { reload: recarregarStreak } = streak;
  const { reload: recarregarAgenda } = agenda;
  const { reload: recarregarDiario } = diario;
  useAoVoltar(
    useCallback(() => {
      recarregarStreak();
      recarregarAgenda();
      recarregarDiario();
    }, [recarregarStreak, recarregarAgenda, recarregarDiario]),
  );

  if (streak.loading && !streak.data && agenda.loading && !agenda.data) return <Carregando />;

  return (
    <Tela>
      {streak.data ? <CartaoStreak streak={streak.data} historico={historico.data} /> : null}
      {streak.error ? <Text style={estilos.erro}>{streak.error.message}</Text> : null}

      <Cartao>
        <View style={styles.cabecalho}>
          <Text style={estilos.subtitulo}>Revise hoje</Text>
          {agenda.data ? <Pilula texto={String(agenda.data.dueCount ?? 0)} /> : null}
        </View>
        {agenda.error ? <Text style={estilos.erro}>{agenda.error.message}</Text> : null}
        {agenda.data && (agenda.data.due?.length ?? 0) === 0 ? (
          <Text style={estilos.dica}>
            Nada vencido hoje. Conclua nós na árvore para começar a alimentar a agenda de revisão.
          </Text>
        ) : null}
        {agenda.data?.due?.map((item) => (
          <Link key={item.nodeCode} href={`/no/${item.nodeCode}`} asChild>
            <Pressable accessibilityRole="link" style={styles.item}>
              <Text style={[estilos.corpo, { flex: 1 }]}>
                <Text style={estilos.codigo}>{item.nodeCode}</Text> {item.title}
              </Text>
              <Pilula texto={atraso(item.daysOverdue ?? 0)} atencao={(item.daysOverdue ?? 0) > 0} />
            </Pressable>
          </Link>
        ))}
      </Cartao>

      <Cartao>
        <Text style={estilos.subtitulo}>Diário</Text>
        <Text style={estilos.dica}>
          {diario.data
            ? `${diario.data.sessionsInMonth ?? 0} ${
                diario.data.sessionsInMonth === 1 ? 'treino registrado' : 'treinos registrados'
              } neste mês.`
            : 'Rola solta, físico ou aula sem nada do currículo também cabem aqui.'}
        </Text>
        <Botao titulo="Registrar treino" onPress={() => router.push('/diario/nova')} />
      </Cartao>
    </Tela>
  );
}

function atraso(dias: number): string {
  if (dias <= 0) return 'para hoje';
  if (dias === 1) return '1 dia atrasado';
  return `${dias} dias atrasado`;
}

const styles = StyleSheet.create({
  cabecalho: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  item: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 6 },
});
