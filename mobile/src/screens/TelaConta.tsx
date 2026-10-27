import { Text, View } from 'react-native';

import { AjusteLembrete } from '../components/AjusteLembrete';
import { ExcluirConta } from '../components/ExcluirConta';
import { Botao, Cartao, Opcao, Tela } from '../components/ui';
import { useConta, useSessao } from '../state/session';
import { OPCOES_DE_TEMA, useTema } from '../state/tema';

/**
 * A conta: quem está dentro, a aparência (D74), o lembrete de revisão (#142), sair e excluir
 * (`DELETE /api/me`, exigência das duas lojas).
 */
export function TelaConta() {
  const { estilos, preferencia, escolher } = useTema();
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
        <Text style={estilos.subtitulo}>Aparência</Text>
        <View accessibilityRole="radiogroup" style={{ flexDirection: 'row', gap: 8 }}>
          {OPCOES_DE_TEMA.map((opcao) => (
            <Opcao
              key={opcao.valor}
              rotulo={opcao.rotulo}
              selecionada={preferencia === opcao.valor}
              onPress={() => escolher(opcao.valor)}
            />
          ))}
        </View>
        <Text style={estilos.dica}>
          Sistema acompanha o modo claro ou escuro do aparelho. A escolha fica só neste aparelho.
        </Text>
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
