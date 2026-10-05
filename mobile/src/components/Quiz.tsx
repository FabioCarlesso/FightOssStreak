import type { QuizQuestionView, QuizResult } from '@fos/types';
import { useState } from 'react';
import { Text, View } from 'react-native';

import { ApiError } from '../api/client';
import { useApi } from '../state/api';
import { cores, estilos } from '../theme';
import { Botao, Opcao } from './ui';

/**
 * Quiz conceitual, como no web: o gabarito só chega no resultado, junto com a explicação de cada
 * pergunta, e o feedback aparece inclusive nos acertos — o valor de retenção está na explicação.
 */
export function Quiz({
  codigo,
  perguntas,
  onConcluido,
}: {
  codigo: string;
  perguntas: readonly QuizQuestionView[];
  onConcluido: () => void;
}) {
  const api = useApi();
  const [respostas, setRespostas] = useState<Record<number, number>>({});
  const [resultado, setResultado] = useState<QuizResult | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);

  if (perguntas.length === 0) {
    return (
      <Text style={estilos.dica}>
        Quiz ainda não escrito para este nó. Registrar o drill abaixo já conclui o nó sem quiz.
      </Text>
    );
  }

  const todasRespondidas = perguntas.every((p) => respostas[p.id ?? -1] !== undefined);

  async function enviar() {
    setEnviando(true);
    setFalha(null);
    try {
      setResultado(
        await api.submitQuiz(codigo, {
          answers: perguntas.map((p) => ({
            questionId: p.id as number,
            optionId: respostas[p.id ?? -1] as number,
          })),
        }),
      );
      onConcluido();
    } catch (causa) {
      setFalha(
        causa instanceof ApiError && causa.isQuizStale
          ? 'O quiz foi renovado. Volte e abra o nó de novo para responder.'
          : causa instanceof Error
            ? causa.message
            : String(causa),
      );
    } finally {
      setEnviando(false);
    }
  }

  if (resultado) {
    return (
      <View style={{ gap: 8 }}>
        <Text
          style={[estilos.subtitulo, { color: resultado.passed ? cores.ok : cores.aviso }]}
          testID="nota-quiz"
        >
          {resultado.score}/100 — {resultado.correctCount} de {resultado.totalQuestions} corretas
        </Text>
        <Text style={estilos.dica}>
          {resultado.passed
            ? 'Nó concluído. O primeiro drill de revisão já foi agendado.'
            : `Mínimo para concluir: ${resultado.passingScore}. Leia as explicações e refaça quando quiser.`}
        </Text>
        {resultado.feedback?.map((item) => (
          <View key={item.questionId} style={{ gap: 2 }}>
            <Text style={[estilos.corpo, { color: item.correct ? cores.ok : cores.erro }]}>
              {item.correct ? '✓' : '✗'} {item.prompt}
            </Text>
            <Text style={estilos.dica}>{item.explanation}</Text>
          </View>
        ))}
        <Botao
          titulo="Refazer o quiz"
          variante="secundario"
          onPress={() => {
            setResultado(null);
            setRespostas({});
          }}
        />
      </View>
    );
  }

  return (
    <View style={{ gap: 12 }}>
      {perguntas.map((pergunta, indice) => (
        <View key={pergunta.id} style={{ gap: 6 }} accessibilityRole="radiogroup">
          <Text style={estilos.corpo}>
            {indice + 1}. {pergunta.prompt}
          </Text>
          {pergunta.options?.map((opcao) => (
            <Opcao
              key={opcao.id}
              rotulo={opcao.label ?? ''}
              selecionada={respostas[pergunta.id ?? -1] === opcao.id}
              onPress={() =>
                setRespostas((atual) => ({
                  ...atual,
                  [pergunta.id as number]: opcao.id as number,
                }))
              }
            />
          ))}
        </View>
      ))}
      {falha ? <Text style={estilos.erro}>{falha}</Text> : null}
      <Botao
        titulo={enviando ? 'Corrigindo…' : 'Responder'}
        desabilitado={!todasRespondidas || enviando}
        onPress={() => void enviar()}
      />
      {!todasRespondidas ? (
        <Text style={estilos.dica}>Responda todas as perguntas para enviar.</Text>
      ) : null}
    </View>
  );
}
