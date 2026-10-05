import { Link, Stack } from 'expo-router';
import { Text } from 'react-native';

import { Drill } from '../components/Drill';
import { Quiz } from '../components/Quiz';
import { Cartao, Carregando, FalhaDaApi, Pilula, Tela } from '../components/ui';
import { Video } from '../components/Video';
import { useApi } from '../state/api';
import { useAsync } from '../state/useAsync';
import { estilos } from '../theme';

/** Linha em branco no `concept` vira parágrafo novo, como no web (#58). */
function paragrafos(conceito: string | undefined): string[] {
  return (conceito ?? '')
    .split(/\n{2,}/)
    .map((p) => p.trim())
    .filter((p) => p.length > 0);
}

/**
 * O nó: conceito, pré-requisitos, vídeo com crédito, quiz e drill avulso — e o aviso curto no fim,
 * que é exigência de produto em todo nó com técnica (`docs/produto/disclaimer.md`).
 *
 * Nó bloqueado mostra conceito e vídeo, mas não deixa responder nem registrar, como no web sem o
 * modo demonstração.
 */
export function TelaNo({ codigo }: { codigo: string }) {
  const api = useApi();
  const no = useAsync(() => api.getNode(codigo), [codigo]);

  // Dado preservado de OUTRO nó não pode aparecer sob este código: o drill gravaria no nó errado.
  const detalhe = no.data?.code === codigo ? no.data : null;

  if (no.error && !detalhe) return <FalhaDaApi erro={no.error} onRetry={no.reload} />;
  if (!detalhe) return <Carregando texto="Carregando nó…" />;

  const bloqueado = detalhe.status === 'LOCKED';

  return (
    <Tela>
      <Stack.Screen options={{ title: detalhe.code ?? codigo }} />
      <Cartao>
        <Text style={estilos.dica}>
          {detalhe.moduleCode} {detalhe.moduleTitle}
        </Text>
        <Text style={estilos.titulo}>
          <Text style={estilos.codigo}>{detalhe.code}</Text> {detalhe.title}
        </Text>
        <Pilula texto={detalhe.belt ?? 'BRANCA'} />
        {bloqueado ? (
          <Text style={estilos.erro}>
            Nó bloqueado.{' '}
            {detalhe.unlockRule === 'ANY'
              ? 'Conclua qualquer um dos pré-requisitos abaixo.'
              : 'Conclua todos os pré-requisitos abaixo.'}
          </Text>
        ) : null}

        <Text style={estilos.subtitulo}>Conceito</Text>
        {paragrafos(detalhe.concept).map((p, i) => (
          <Text key={i} style={estilos.corpo}>
            {p}
          </Text>
        ))}

        {(detalhe.prereqs?.length ?? 0) > 0 ? (
          <>
            <Text style={estilos.subtitulo}>Pré-requisitos</Text>
            {detalhe.prereqs?.map((pre) => (
              <Link key={pre.code} href={`/no/${pre.code}`} style={estilos.corpo}>
                {pre.completed ? '✓' : '○'} {pre.code} — {pre.title}
              </Link>
            ))}
          </>
        ) : null}
      </Cartao>

      <Cartao>
        <Text style={estilos.subtitulo}>Vídeo de referência</Text>
        <Video video={detalhe.video} />
      </Cartao>

      {!bloqueado ? (
        <Cartao>
          <Text style={estilos.subtitulo}>Quiz conceitual</Text>
          <Quiz
            key={codigo}
            codigo={codigo}
            perguntas={detalhe.quiz ?? []}
            onConcluido={no.reload}
          />
        </Cartao>
      ) : null}

      {!bloqueado ? (
        <Cartao>
          <Text style={estilos.subtitulo}>Registrar drill</Text>
          <Drill key={codigo} codigo={codigo} srs={detalhe.srs} onRegistrado={no.reload} />
        </Cartao>
      ) : null}

      {detalhe.safetyNotice ? (
        <Text style={estilos.dica} testID="aviso-curto">
          {detalhe.safetyNotice}
        </Text>
      ) : null}
    </Tela>
  );
}
