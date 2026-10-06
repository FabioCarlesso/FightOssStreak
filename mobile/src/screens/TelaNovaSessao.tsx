import {
  emptySessionFields,
  FEELING_LABELS,
  RECALL_LABELS,
  SESSION_KIND_LABELS,
  todayIso,
  toSessionBody,
  type Recall,
  type SessionFieldsState,
} from '@fos/domain';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { StyleSheet, Text, TextInput, View, type TextInputProps } from 'react-native';

import { Botao, Cartao, Opcao, Tela } from '../components/ui';
import { useApi } from '../state/api';
import { useLembretes } from '../state/lembretes';
import { useAsync } from '../state/useAsync';
import { cores, estilos } from '../theme';

const DATA = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Registrar um treino (#114, D56), com as mesmas regras da tela do web.
 *
 * **Atrito é o que faz o registro não acontecer:** só a data é obrigatória, ela já vem com hoje, e
 * salvar com um toque é caminho completo. As técnicas vêm do que o SRS marcou para hoje, e nenhuma
 * vem marcada — marcar por padrão registraria treino que não houve. Descanso não recebe técnica.
 *
 * A conversão do formulário para o corpo da requisição é `toSessionBody`, de `@fos/domain`, a mesma
 * do web: campo vazio vira "não informado", nunca zero.
 */
export function TelaNovaSessao() {
  const api = useApi();
  const router = useRouter();
  const { aposRegistro } = useLembretes();
  const hoje = todayIso();
  const [campos, setCampos] = useState<SessionFieldsState>(() => emptySessionFields(hoje));
  const [tecnicas, setTecnicas] = useState<Record<string, Recall>>({});
  const [salvando, setSalvando] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);
  const agenda = useAsync(() => api.getReviewsToday(), []);

  const descanso = campos.kind === 'DESCANSO';
  const dataValida = DATA.test(campos.trainedOn) && campos.trainedOn <= hoje;

  function mudar<K extends keyof SessionFieldsState>(chave: K, valor: SessionFieldsState[K]) {
    setCampos((atual) => ({ ...atual, [chave]: valor }));
  }

  function alternar(codigo: string) {
    setTecnicas((atual) => {
      const proximo = { ...atual };
      if (proximo[codigo]) delete proximo[codigo];
      else proximo[codigo] = 'OK';
      return proximo;
    });
  }

  async function registrar() {
    setSalvando(true);
    setFalha(null);
    try {
      await api.createTrainingSession({
        ...toSessionBody(campos),
        tecnicas: descanso
          ? []
          : Object.entries(tecnicas).map(([nodeCode, recall]) => ({ nodeCode, recall })),
      });
      aposRegistro();
      router.back();
    } catch (causa) {
      setFalha(causa instanceof Error ? causa.message : String(causa));
      setSalvando(false);
    }
  }

  return (
    <Tela>
      <Cartao>
        <Text style={estilos.dica}>
          Só a data é obrigatória. Salve agora e complete depois — o registro incompleto já conta.
        </Text>
        <Campo
          rotulo="Data * (AAAA-MM-DD)"
          value={campos.trainedOn}
          onChangeText={(v) => mudar('trainedOn', v)}
          autoCapitalize="none"
        />
        {!dataValida ? (
          <Text style={estilos.erro}>Use uma data no formato AAAA-MM-DD, até hoje.</Text>
        ) : null}

        <Text style={estilos.subtitulo}>Tipo</Text>
        <View style={styles.opcoes}>
          {SESSION_KIND_LABELS.map((o) => (
            <Opcao
              key={o.value}
              rotulo={o.label}
              selecionada={campos.kind === o.value}
              onPress={() => mudar('kind', o.value)}
            />
          ))}
        </View>
        {descanso ? (
          <Text style={estilos.dica}>
            Descanso fica no diário e não conta no streak — e não recebe técnica do currículo.
          </Text>
        ) : null}

        <View style={styles.linha}>
          <View style={{ flex: 1 }}>
            <Campo
              rotulo="Duração (min)"
              keyboardType="number-pad"
              value={campos.durationMinutes}
              onChangeText={(v) => mudar('durationMinutes', v)}
            />
          </View>
          <View style={{ flex: 1 }}>
            <Campo
              rotulo="Peso (kg)"
              keyboardType="decimal-pad"
              value={campos.weightKg}
              onChangeText={(v) => mudar('weightKg', v)}
            />
          </View>
        </View>

        {/* Sem meta, sem faixa saudável e sem alerta: o app guarda e mostra (D57). */}
        <Text style={estilos.subtitulo}>Sensação</Text>
        <View style={styles.opcoes}>
          {FEELING_LABELS.map((o) => (
            <Opcao
              key={o.value}
              rotulo={`${o.icon} ${o.label}`}
              selecionada={campos.feeling === o.value}
              onPress={() => mudar('feeling', o.value)}
            />
          ))}
          <Opcao
            rotulo="Não dizer"
            selecionada={campos.feeling === ''}
            onPress={() => mudar('feeling', '')}
          />
        </View>

        <Campo
          rotulo="O que aprendi"
          multiline
          maxLength={2000}
          value={campos.learned}
          onChangeText={(v) => mudar('learned', v)}
        />
        <Campo
          rotulo="O que preciso melhorar"
          multiline
          maxLength={2000}
          value={campos.improve}
          onChangeText={(v) => mudar('improve', v)}
        />
      </Cartao>

      {!descanso ? (
        <Cartao>
          <Text style={estilos.subtitulo}>Técnicas do currículo</Text>
          <Text style={estilos.dica}>
            O que o SRS marcou para hoje. Marcar aqui é o mesmo que registrar o drill na tela do nó.
          </Text>
          {agenda.data && (agenda.data.due?.length ?? 0) === 0 ? (
            <Text style={estilos.dica}>Nada vencido hoje.</Text>
          ) : null}
          {agenda.data?.due?.map((item) => {
            const codigo = item.nodeCode ?? '';
            const marcada = tecnicas[codigo] != null;
            return (
              <View key={codigo} style={{ gap: 6 }}>
                <Opcao
                  rotulo={`${codigo} ${item.title ?? ''}`}
                  selecionada={marcada}
                  onPress={() => alternar(codigo)}
                />
                {marcada ? (
                  <View style={styles.opcoes}>
                    {RECALL_LABELS.map((o) => (
                      <Opcao
                        key={o.value}
                        rotulo={o.label}
                        selecionada={tecnicas[codigo] === o.value}
                        onPress={() => setTecnicas((atual) => ({ ...atual, [codigo]: o.value }))}
                      />
                    ))}
                  </View>
                ) : null}
              </View>
            );
          })}
        </Cartao>
      ) : null}

      {falha ? <Text style={estilos.erro}>{falha}</Text> : null}
      <Botao
        titulo={salvando ? 'Registrando…' : 'Registrar treino'}
        desabilitado={salvando || !dataValida}
        onPress={() => void registrar()}
      />
    </Tela>
  );
}

function Campo({ rotulo, multiline, ...props }: { rotulo: string } & TextInputProps) {
  return (
    <View style={{ gap: 4 }}>
      <Text style={estilos.dica}>{rotulo}</Text>
      <TextInput
        accessibilityLabel={rotulo}
        placeholderTextColor={cores.dica}
        multiline={multiline}
        style={[styles.campo, multiline && { minHeight: 60, textAlignVertical: 'top' }]}
        {...props}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  opcoes: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  linha: { flexDirection: 'row', gap: 12 },
  campo: {
    borderWidth: 1,
    borderColor: cores.borda,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 10,
    color: cores.texto,
    fontSize: 15,
  },
});
