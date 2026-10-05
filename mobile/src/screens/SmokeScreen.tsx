import { addDays, currentStreak, type IsoDate } from '@fos/domain';
import type { AuthProviders } from '@fos/types';
import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';

import { apiUrl as defaultApiUrl, type Api } from '../api/client';

/**
 * Tela de fumaça do workspace mobile (#140). Não é tela do produto: ela existe para provar, no
 * aparelho, as duas costuras que o app inteiro vai usar — a regra pura de `@fos/domain` rodando
 * no Hermes e o `@fos/api-client` falando com o backend por uma rota pública.
 */
export function SmokeScreen({
  api,
  apiUrl = defaultApiUrl,
  today = isoToday(),
}: {
  api: Api;
  apiUrl?: string;
  today?: IsoDate;
}) {
  const [providers, setProviders] = useState<Estado>({ fase: 'carregando' });

  useEffect(() => {
    if (!apiUrl) {
      return;
    }
    let vivo = true;
    api.getAuthProviders().then(
      (resposta) => vivo && setProviders({ fase: 'ok', resposta }),
      (erro: unknown) =>
        vivo &&
        setProviders({ fase: 'erro', mensagem: erro instanceof Error ? erro.message : 'falhou' }),
    );
    return () => {
      vivo = false;
    };
  }, [api, apiUrl]);

  // Três dias seguidos terminando hoje: se o domínio compartilhado carregou, a resposta é 3.
  const streak = currentStreak([addDays(today, -2), addDays(today, -1), today], today);

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.title}>FightOssStreak</Text>
      <Text style={styles.subtitle}>Tela de fumaça do app (#140)</Text>

      <View style={styles.card}>
        <Text style={styles.label}>@fos/domain</Text>
        <Text style={styles.value} testID="streak">
          currentStreak de 3 dias seguidos = {streak}
        </Text>
      </View>

      <View style={styles.card}>
        <Text style={styles.label}>GET /api/auth/providers</Text>
        {!apiUrl ? (
          <Text style={styles.warning} testID="providers">
            Defina EXPO_PUBLIC_API_URL (ver mobile/.env.example) e reinicie o expo start.
          </Text>
        ) : (
          <Text
            style={providers.fase === 'erro' ? styles.warning : styles.value}
            testID="providers"
          >
            {descrever(providers)}
          </Text>
        )}
        {apiUrl ? <Text style={styles.hint}>{apiUrl}</Text> : null}
      </View>
    </ScrollView>
  );
}

type Estado =
  | { fase: 'carregando' }
  | { fase: 'ok'; resposta: AuthProviders }
  | { fase: 'erro'; mensagem: string };

function descrever(estado: Estado): string {
  switch (estado.fase) {
    case 'carregando':
      return 'Consultando a API...';
    case 'erro':
      return `A API não respondeu: ${estado.mensagem}`;
    case 'ok': {
      const { resposta } = estado;
      const portas = [
        ...(resposta.passwordEnabled ? ['senha'] : []),
        ...(resposta.mobileProviders ?? []),
      ];
      return portas.length > 0
        ? `API no ar. Entradas do app: ${portas.join(', ')}`
        : 'API no ar. Nenhuma entrada do app configurada neste ambiente.';
    }
  }
}

function isoToday(): IsoDate {
  return new Date().toISOString().slice(0, 10);
}

const styles = StyleSheet.create({
  container: {
    flexGrow: 1,
    backgroundColor: '#0e0f13',
    padding: 24,
    paddingTop: 72,
    gap: 16,
  },
  title: { color: '#f2f2f2', fontSize: 24, fontWeight: '700' },
  subtitle: { color: '#9a9ba3', fontSize: 14 },
  card: {
    backgroundColor: '#181a20',
    borderColor: '#2a2c33',
    borderWidth: 1,
    borderRadius: 12,
    padding: 16,
    gap: 6,
  },
  label: { color: '#e8743b', fontSize: 13, fontWeight: '600' },
  value: { color: '#f2f2f2', fontSize: 15 },
  warning: { color: '#f2a65a', fontSize: 15 },
  hint: { color: '#6b6d75', fontSize: 12 },
});
