import { FULL_DISCLAIMER } from '@fos/domain';
import { useState } from 'react';
import { Text } from 'react-native';

import { ExcluirConta } from '../components/ExcluirConta';
import { Botao, Cartao, Tela } from '../components/ui';
import { useApi } from '../state/api';
import { useSessao } from '../state/session';
import { estilos } from '../theme';

/**
 * As telas que um portão mostra no lugar do app: atualização obrigatória, conta bloqueada e aviso
 * de responsabilidade. Nenhuma é rota — o app inteiro fica atrás delas, como no web.
 */

/** O app está abaixo da versão mínima que a API atende (#139). */
export function TelaAtualizar({ minima }: { minima: string }) {
  return (
    <Tela segura>
      <Cartao>
        <Text style={estilos.titulo}>Atualize o app</Text>
        <Text style={estilos.corpo}>
          Esta versão do FightOssStreak não conversa mais com o servidor. Atualize pela loja para
          continuar — nada do seu progresso se perde, ele fica guardado na sua conta.
        </Text>
        <Text style={estilos.dica}>Versão mínima: {minima}</Text>
      </Cartao>
    </Tela>
  );
}

/**
 * A conta está bloqueada (#90). O texto explica e para, como no web: sermão não devolve o acesso,
 * e quem lê pode ter sido bloqueado por engano. Sair e excluir a conta continuam funcionando —
 * bloquear não pode virar sequestro de dado pessoal.
 */
export function TelaBloqueada() {
  const { sair, contaExcluida } = useSessao();
  return (
    <Tela segura>
      <Cartao>
        <Text style={estilos.titulo}>Seu acesso está bloqueado</Text>
        <Text style={estilos.corpo}>
          Uma conta de administração do FightOssStreak interrompeu o acesso desta conta. Enquanto
          isso valer, o app não abre — mas nada foi apagado: progresso, streak, agenda de revisão e
          anotações continuam onde estavam.
        </Text>
        <Text style={estilos.dica}>
          Se isso parece engano, responda ao e-mail pelo qual você recebe as mensagens do app.
        </Text>
        <Botao titulo="Sair" variante="secundario" onPress={() => void sair()} />
      </Cartao>
      <Cartao>
        <Text style={estilos.dica}>
          Se preferir não esperar, a conta é sua e continua sendo: dá para excluí-la agora.
        </Text>
        <ExcluirConta onExcluida={() => void contaExcluida()} />
      </Cartao>
    </Tela>
  );
}

/**
 * Aceite do aviso, por versão do texto (regra 5 do CLAUDE.md). O texto é o mesmo do web, lido de
 * `@fos/domain`; a versão vigente vem do backend.
 */
export function TelaAviso({
  versao,
  nome,
  onAceito,
}: {
  versao: string;
  nome?: string;
  onAceito: () => void;
}) {
  const api = useApi();
  const { sair } = useSessao();
  const [aceitando, setAceitando] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);

  async function aceitar() {
    setAceitando(true);
    setFalha(null);
    try {
      await api.acceptDisclaimer(versao);
      onAceito();
    } catch (causa) {
      setFalha(causa instanceof Error ? causa.message : String(causa));
      setAceitando(false);
    }
  }

  return (
    <Tela segura>
      <Cartao>
        <Text style={estilos.titulo}>Aviso importante — leia antes de usar</Text>
        {FULL_DISCLAIMER.map((paragrafo) => (
          <Text key={paragrafo.slice(0, 40)} style={estilos.corpo}>
            {paragrafo}
          </Text>
        ))}
        {falha ? <Text style={estilos.erro}>{falha}</Text> : null}
        <Botao
          titulo={aceitando ? 'Registrando…' : 'Li e concordo'}
          desabilitado={aceitando}
          onPress={() => void aceitar()}
        />
        <Text style={estilos.dica}>Versão do texto: {versao}</Text>
      </Cartao>
      <Cartao>
        <Text style={estilos.dica}>{nome ? `Entrou como ${nome}.` : 'Conta errada?'}</Text>
        <Botao titulo="Sair" variante="secundario" onPress={() => void sair()} />
      </Cartao>
    </Tela>
  );
}
