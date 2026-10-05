import type { NodeSummary } from '@fos/types';
import { Link } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';

import { Cartao, Carregando, FalhaDaApi, Pilula, Tela } from '../components/ui';
import { useApi } from '../state/api';
import { useAoVoltar } from '../state/useAoVoltar';
import { useAsync } from '../state/useAsync';
import { cores, estilos } from '../theme';

/**
 * Árvore do currículo, com o bloqueio já resolvido pelo backend, como no web. O modo demonstração
 * do web não vem para o app: ele existe para inspecionar o currículo no navegador.
 */
export function TelaArvore() {
  const api = useApi();
  const arvore = useAsync(() => api.getTree(), []);
  useAoVoltar(arvore.reload);

  if (arvore.loading && !arvore.data) return <Carregando texto="Carregando currículo…" />;
  if (arvore.error && !arvore.data)
    return <FalhaDaApi erro={arvore.error} onRetry={arvore.reload} />;
  if (!arvore.data) return null;

  const resumo = arvore.data.summary;

  return (
    <Tela>
      <Cartao>
        <Text style={estilos.subtitulo}>Progresso</Text>
        <Text style={estilos.dica} testID="resumo-arvore">
          {resumo?.completedNodes ?? 0} concluídos · {resumo?.availableNodes ?? 0} disponíveis ·{' '}
          {resumo?.lockedNodes ?? 0} bloqueados · {resumo?.totalNodes ?? 0} nós no total
        </Text>
      </Cartao>

      {arvore.data.modules?.map((modulo) => (
        <Cartao key={modulo.code}>
          <Text style={estilos.subtitulo}>
            <Text style={estilos.codigo}>{modulo.code}</Text> {modulo.title}
          </Text>
          {modulo.summary ? <Text style={estilos.dica}>{modulo.summary}</Text> : null}
          {modulo.nodes?.map((no) => (
            <LinhaDoNo key={no.code} no={no} />
          ))}
        </Cartao>
      ))}
    </Tela>
  );
}

function LinhaDoNo({ no }: { no: NodeSummary }) {
  const bloqueado = no.status === 'LOCKED';
  const corpo = (
    <View style={[styles.linha, bloqueado && { opacity: 0.55 }]}>
      <Text style={styles.icone}>{icone(no.status)}</Text>
      <View style={{ flex: 1, gap: 2 }}>
        <Text style={estilos.corpo}>
          <Text style={estilos.codigo}>{no.code}</Text> {no.title}
        </Text>
        {bloqueado ? (
          <Text style={estilos.dica}>
            {no.unlockRule === 'ANY'
              ? `Conclua qualquer um: ${(no.prereqCodes ?? []).join(', ')}`
              : `Requer: ${(no.prereqCodes ?? []).join(', ')}`}
          </Text>
        ) : null}
      </View>
      <Pilula texto={no.belt ?? 'BRANCA'} />
    </View>
  );

  if (bloqueado) return corpo;
  return (
    <Link href={`/no/${no.code}`} asChild>
      <Pressable accessibilityRole="link">{corpo}</Pressable>
    </Link>
  );
}

function icone(status: NodeSummary['status']): string {
  switch (status) {
    case 'COMPLETED':
      return '✓';
    case 'IN_PROGRESS':
      return '◐';
    case 'LOCKED':
      return '🔒';
    default:
      return '○';
  }
}

const styles = StyleSheet.create({
  linha: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    paddingVertical: 8,
    borderTopWidth: 1,
    borderTopColor: cores.borda,
  },
  icone: { color: cores.texto, width: 20, textAlign: 'center' },
});
