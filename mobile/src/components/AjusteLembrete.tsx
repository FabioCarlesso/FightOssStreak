import { Linking, StyleSheet, Switch, Text, View } from 'react-native';

import { formatarHorario, moverHorario } from '../lembretes/plano';
import { useLembretes } from '../state/lembretes';
import { cores, estilos } from '../theme';
import { Botao } from './ui';

/**
 * Ligar, desligar e mudar o horário do lembrete de revisão (#142). Fica guardado no aparelho.
 *
 * Sem permissão do sistema, o cartão diz o que fazer em vez de fingir que o lembrete está ligado:
 * quem negou só reativa nos ajustes do aparelho, porque o sistema não deixa o app perguntar de novo.
 */
export function AjusteLembrete() {
  const { preferencias, permissao, salvar, pedirPermissao } = useLembretes();

  if (!preferencias || !permissao) {
    return <Text style={estilos.dica}>Conferindo as notificações do aparelho…</Text>;
  }

  if (permissao === 'indisponivel') {
    return (
      <Text style={estilos.dica}>
        Este aparelho não roda o lembrete: no Android, o Expo Go não traz o módulo de notificações.
        Ele funciona no app instalado (dev build ou loja).
      </Text>
    );
  }

  if (permissao === 'indefinida') {
    return (
      <View style={styles.coluna}>
        <Text style={estilos.dica}>
          O app pode avisar quando houver técnica vencida na agenda de revisão.
        </Text>
        <Botao
          titulo="Ativar lembretes"
          variante="secundario"
          onPress={() => void pedirPermissao()}
        />
      </View>
    );
  }

  if (permissao === 'negada') {
    return (
      <View style={styles.coluna}>
        <Text style={estilos.dica}>
          As notificações do app estão desligadas nos ajustes do aparelho. Para receber o lembrete,
          ative-as por lá.
        </Text>
        <Botao
          titulo="Abrir ajustes do aparelho"
          variante="secundario"
          onPress={() => void Linking.openSettings()}
        />
      </View>
    );
  }

  return (
    <View style={styles.coluna}>
      <View style={styles.linha}>
        <Text style={[estilos.corpo, { flex: 1 }]}>Avisar quando houver revisão vencida</Text>
        <Switch
          accessibilityLabel="Lembrete de revisão"
          value={preferencias.ligado}
          onValueChange={(ligado) => void salvar({ ...preferencias, ligado })}
          trackColor={{ true: cores.destaque, false: cores.borda }}
        />
      </View>
      {preferencias.ligado ? (
        <View style={styles.linha}>
          <Text style={[estilos.corpo, { flex: 1 }]}>Horário</Text>
          <Botao
            titulo="−"
            variante="secundario"
            onPress={() => void salvar(moverHorario(preferencias, -1))}
          />
          <Text style={styles.horario} accessibilityLabel="Horário do lembrete">
            {formatarHorario(preferencias)}
          </Text>
          <Botao
            titulo="+"
            variante="secundario"
            onPress={() => void salvar(moverHorario(preferencias, 1))}
          />
        </View>
      ) : null}
      <Text style={estilos.dica}>
        O aviso só diz quantas técnicas venceram, sem o nome delas, e só chega nos dias em que
        houver alguma.
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  coluna: { gap: 8 },
  linha: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  horario: {
    color: cores.texto,
    fontSize: 16,
    fontWeight: '600',
    minWidth: 56,
    textAlign: 'center',
  },
});
