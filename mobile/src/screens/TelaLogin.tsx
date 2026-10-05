import * as WebBrowser from 'expo-web-browser';
import { useState } from 'react';
import { StyleSheet, Text, TextInput, View } from 'react-native';

import { ApiError, webUrl as webUrlPadrao } from '../api/client';
import { Botao, Cartao, Tela } from '../components/ui';
import { useSessao } from '../state/session';
import { cores, estilos } from '../theme';

/**
 * Entrada por e-mail e senha (D47, D68). Google e Apple entram depois da #143, quando houver
 * credencial e dev build para testá-los no aparelho.
 *
 * Cadastro e recuperação de senha abrem a web, como a D68 decidiu: o link de confirmação sai de
 * `fos.public-url` e a confirmação exige a senha do cadastro (D61, D62), e as duas telas já existem
 * lá. As respostas de erro são as mesmas do login da web — 401 sem dizer se o e-mail existe.
 */
export function TelaLogin({ webUrl = webUrlPadrao }: { webUrl?: string }) {
  const { entrar } = useSessao();
  const [email, setEmail] = useState('');
  const [senha, setSenha] = useState('');
  const [entrando, setEntrando] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);

  async function enviar() {
    setEntrando(true);
    setFalha(null);
    try {
      await entrar(email, senha);
    } catch (causa) {
      setFalha(mensagemDe(causa));
      setEntrando(false);
    }
  }

  function abrirNaWeb(caminho: string) {
    if (webUrl) void WebBrowser.openBrowserAsync(`${webUrl.replace(/\/$/, '')}${caminho}`);
  }

  return (
    <Tela segura>
      <View style={styles.cabecalho}>
        <Text style={estilos.titulo}>FightOssStreak</Text>
        <Text style={estilos.dica}>Revisar o que você viu no tatame</Text>
      </View>

      <Cartao>
        <Text style={estilos.subtitulo}>Entrar</Text>
        <TextInput
          accessibilityLabel="E-mail"
          placeholder="E-mail"
          placeholderTextColor={cores.dica}
          autoCapitalize="none"
          autoComplete="email"
          keyboardType="email-address"
          style={styles.campo}
          value={email}
          onChangeText={setEmail}
        />
        <TextInput
          accessibilityLabel="Senha"
          placeholder="Senha"
          placeholderTextColor={cores.dica}
          secureTextEntry
          autoComplete="current-password"
          style={styles.campo}
          value={senha}
          onChangeText={setSenha}
          onSubmitEditing={() => void enviar()}
        />
        {falha ? <Text style={estilos.erro}>{falha}</Text> : null}
        <Botao
          titulo={entrando ? 'Entrando…' : 'Entrar'}
          desabilitado={entrando || !email.trim() || !senha}
          onPress={() => void enviar()}
        />
      </Cartao>

      <Cartao>
        <Text style={estilos.dica}>
          Ainda não tem conta, ou esqueceu a senha? As duas coisas são feitas no site, e depois você
          entra aqui com o e-mail e a senha.
        </Text>
        {webUrl ? (
          <>
            <Botao
              titulo="Criar conta no site"
              variante="secundario"
              onPress={() => abrirNaWeb('/cadastrar')}
            />
            <Botao
              titulo="Esqueci a senha"
              variante="secundario"
              onPress={() => abrirNaWeb('/senha/esquecida')}
            />
          </>
        ) : (
          <Text style={estilos.dica}>
            Defina EXPO_PUBLIC_WEB_URL (ver mobile/.env.example) para abrir o site daqui.
          </Text>
        )}
      </Cartao>
    </Tela>
  );
}

function mensagemDe(causa: unknown): string {
  if (causa instanceof ApiError && causa.code === 'email_nao_verificado') {
    return `${causa.message} Procure o link de confirmação no seu e-mail.`;
  }
  return causa instanceof Error ? causa.message : String(causa);
}

const styles = StyleSheet.create({
  cabecalho: { paddingTop: 24, paddingBottom: 8, gap: 4 },
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
