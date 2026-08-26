import { ApiError } from '../../api/client.ts';

/**
 * Erro do backend virado em frase que a pessoa consegue usar.
 *
 * A mensagem do servidor já é escrita para humano, então o default é mostrá-la. O que este arquivo
 * acrescenta são os casos em que a tela sabe algo que o backend não sabe — que existe um botão de
 * reenviar logo abaixo, por exemplo — e os casos em que a frase do servidor é técnica demais para
 * a fronteira do app.
 *
 * **A regra que não se negocia aqui**: nenhuma mensagem pode revelar se um endereço tem conta. O
 * backend já responde igual para os dois casos (D47); traduzir mal desfaria isso do lado do
 * cliente, que é onde ninguém procuraria o vazamento.
 */
export function mensagemDeErro(cause: unknown): string {
  if (cause instanceof ApiError) {
    if (cause.isBadCredentials) {
      // Deliberadamente sobre o par, nunca sobre o e-mail: "não existe conta com este e-mail"
      // transformaria a tela de entrada em consulta de quem usa o app.
      return 'E-mail ou senha não conferem.';
    }
    if (cause.isTooManyAttempts) {
      return 'Tentativas demais nesta conta. Espere alguns minutos antes de tentar de novo.';
    }
    if (cause.isSignUpUnavailable) {
      return (
        'O cadastro por e-mail e senha não está disponível neste ambiente, porque não há' +
        ' provedor de envio de e-mail configurado — e é o e-mail de confirmação que faz a conta' +
        ' existir. Entre por um provedor, ou configure o envio (README).'
      );
    }
    if (cause.status === 429) {
      return 'Você pediu isso vezes demais em pouco tempo. Espere um pouco e tente de novo.';
    }
  }
  return cause instanceof Error ? cause.message : String(cause);
}
