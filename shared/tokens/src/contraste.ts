/**
 * Razão de contraste da WCAG 2.x, para o teste que segura a régua da #181 nos dois temas (#187).
 *
 * Aceita `#rrggbb`, `#rgb`, `rgb(r g b / a%)` e `rgba(r, g, b, a)`. Cor translúcida é composta
 * sobre o fundo antes da conta — é o que o navegador pinta —, e por isso o fundo precisa ser opaco.
 */

type Rgba = readonly [r: number, g: number, b: number, a: number];

export function lerCor(cor: string): Rgba {
  const hex = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(cor.trim());
  if (hex) {
    const digitos = hex[1] ?? '';
    const h = digitos.length === 3 ? digitos.replace(/./g, '$&$&') : digitos;
    const canal = (i: number) => parseInt(h.slice(i, i + 2), 16);
    return [canal(0), canal(2), canal(4), 1];
  }
  const fn = /^rgba?\(([^)]+)\)$/i.exec(cor.trim());
  if (fn) {
    const partes = (fn[1] ?? '').split(/[\s,/]+/).filter(Boolean);
    const [r = NaN, g = NaN, b = NaN] = partes.slice(0, 3).map(Number);
    const bruto = partes[3];
    const a =
      bruto === undefined ? 1 : bruto.endsWith('%') ? parseFloat(bruto) / 100 : Number(bruto);
    if ([r, g, b, a].every((n) => Number.isFinite(n))) return [r, g, b, a];
  }
  throw new Error(`cor que o teste de contraste não sabe ler: ${cor}`);
}

/** Pinta `frente` (talvez translúcida) sobre `fundo` opaco. */
export function compor(frente: string, fundo: string): Rgba {
  const [r, g, b, a] = lerCor(frente);
  const [fr, fg, fb, fa] = lerCor(fundo);
  if (fa !== 1) throw new Error(`fundo translúcido não tem contraste definido: ${fundo}`);
  return [r * a + fr * (1 - a), g * a + fg * (1 - a), b * a + fb * (1 - a), 1];
}

function luminancia([r, g, b]: Rgba): number {
  const canal = (c: number) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * canal(r) + 0.7152 * canal(g) + 0.0722 * canal(b);
}

/** A razão de `frente` sobre `fundo`, de 1 a 21. */
export function razaoDeContraste(frente: string, fundo: string): number {
  const a = luminancia(compor(frente, fundo));
  const b = luminancia(lerCor(fundo));
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}
