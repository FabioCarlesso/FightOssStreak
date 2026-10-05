/**
 * O app está abaixo da versão mínima que a API atende (#139)?
 *
 * Compara `1.2.3` por número em cada parte, e não como texto: como texto, `1.10.0` viria antes de
 * `1.9.0`. Sem mínimo, ou com versão ilegível, a resposta é "não": a tela de atualização trava o
 * app inteiro, e travar por um valor malformado seria pior que deixar passar.
 */
export function abaixoDaMinima(atual: string | undefined, minima: string | null | undefined) {
  if (!atual || !minima) return false;
  const a = partes(atual);
  const m = partes(minima);
  if (!a || !m) return false;
  for (let i = 0; i < Math.max(a.length, m.length); i++) {
    const x = a[i] ?? 0;
    const y = m[i] ?? 0;
    if (x !== y) return x < y;
  }
  return false;
}

function partes(versao: string): number[] | null {
  const numeros = versao.trim().split('.').map(Number);
  return numeros.every((n) => Number.isInteger(n) && n >= 0) ? numeros : null;
}
