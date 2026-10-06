import { Text } from 'react-native';

import { AjusteLembrete } from '../components/AjusteLembrete';
import { ExcluirConta } from '../components/ExcluirConta';
import { Botao, Cartao, Tela } from '../components/ui';
import { useConta, useSessao } from '../state/session';
import { estilos } from '../theme';

/**
 * A conta: quem está dentro, o lembrete de revisão (#142), sair e excluir (`DELETE /api/me`,
 * exigência das duas lojas).
 */
export function TelaConta() {
  const conta = useConta();
  const { sair, contaExcluida } = useSessao();

  return (
    <Tela>
      <Cartao>
        <Text style={estilos.subtitulo}>{conta.displayName}</Text>
        <Text style={estilos.dica}>{conta.email}</Text>
        <Botao titulo="Sair" variante="secundario" onPress={() => void sair()} />
      </Cartao>
      <Cartao>
        <Text style={estilos.subtitulo}>Lembrete de revisão</Text>
        <AjusteLembrete />
      </Cartao>
      <Cartao>
        <Text style={estilos.subtitulo}>Excluir conta</Text>
        <Text style={estilos.dica}>
          A conta é sua: excluí-la apaga tudo que é dela, aqui e no site, e o app volta para o
          login.
        </Text>
        <ExcluirConta onExcluida={() => void contaExcluida()} />
      </Cartao>
    </Tela>
  );
}
