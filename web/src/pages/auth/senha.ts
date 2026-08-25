/**
 * A regra de senha, do lado de cá.
 *
 * Duplica de propósito o que `PasswordPolicy` decide no backend, e a duplicação tem escopo: aqui
 * ela serve para **dizer antes**, não para autorizar. Quem recusa é sempre o servidor — o cliente
 * só evita que a pessoa descubra o mínimo depois de preencher o formulário inteiro.
 *
 * Mudou lá? Muda aqui. A divergência aceitável é o cliente ser mais frouxo (deixa passar e o
 * servidor explica); a inaceitável é ser mais rígido, porque aí ele barra o que o app aceitaria.
 */

/** Igual ao `PasswordPolicy.MINIMO`. */
export const SENHA_MINIMA = 12;

/**
 * Teto do bcrypt, em **bytes** — acento e emoji contam mais de um.
 *
 * Não é capricho: acima disso o Spring Security recusa a entrada em vez de truncar em silêncio.
 */
export const SENHA_MAXIMA_BYTES = 72;

export const REGRA_SENHA = `Pelo menos ${SENHA_MINIMA} caracteres. Sem exigência de maiúscula, número ou símbolo — o que mede força é o tamanho.`;

/** Quantos bytes a senha ocupa em UTF-8, que é o que o bcrypt conta. */
function bytes(senha: string): number {
  return new TextEncoder().encode(senha).length;
}

/** Nulo quando a senha passa; a explicação quando não. */
export function problemaNaSenha(senha: string): string | null {
  if (senha.length === 0) return null;
  if (senha.length < SENHA_MINIMA) {
    return `Faltam ${SENHA_MINIMA - senha.length} caractere(s) para chegar aos ${SENHA_MINIMA}.`;
  }
  if (bytes(senha) > SENHA_MAXIMA_BYTES) {
    return `Senha longa demais (máximo de ${SENHA_MAXIMA_BYTES} bytes; acentos e emoji contam mais de um).`;
  }
  return null;
}

export type Forca = 'curta' | 'ok' | 'boa' | 'ótima';

/**
 * Medidor simples, e simples é a decisão.
 *
 * Ele mede **tamanho e variedade de caracteres**, não composição obrigatória: a linha do NIST SP
 * 800-63B é que exigir maiúscula e símbolo produz `Senha@2026`, que é pior do que parece. Por isso
 * a barra nunca reprova nada além do mínimo — ela informa, e quem decide é quem digita.
 */
export function forcaDaSenha(senha: string): Forca {
  if (senha.length < SENHA_MINIMA) return 'curta';
  const variedade = new Set(senha).size;
  if (senha.length >= 20 && variedade >= 10) return 'ótima';
  if (senha.length >= 16 || variedade >= 10) return 'boa';
  return 'ok';
}
