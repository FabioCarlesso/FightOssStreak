import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { compor, razaoDeContraste } from './contraste.ts';
import { temas, type ChaveDeCor, type Cores, type NomeDoTema } from './index.ts';

/**
 * A régua de contraste da #181, nos dois temas (#187, D74). A conferência deixa de depender de
 * alguém abrir o DevTools: um par abaixo do mínimo reprova o `npm test`.
 *
 * - Texto: 4,5:1 (WCAG 1.4.3, AA).
 * - Borda de controle, anel de foco e o anel do dia perdoado: 3:1 (WCAG 1.4.11).
 *
 * O anel de foco do web é `--focus` (#186, base.css), com valor próprio em cada tema.
 */

const TEXTO = 4.5;
const NAO_TEXTO = 3;

type Par = {
  frente: ChaveDeCor | '#ffffff';
  fundo: ChaveDeCor;
  sobre?: ChaveDeCor;
  minimo: number;
};

/** Fundos em que há texto: a página, o cartão e o cartão de dentro. */
const FUNDOS = ['bg', 'surface', 'surface2'] as const;

const PARES: Par[] = [
  // Texto corrido, texto fraco, link e código, e os estados escritos (erro, ok, aviso do app).
  ...(['text', 'muted', 'accent', 'warn', 'ok', 'caution'] as const).flatMap((frente) =>
    FUNDOS.map((fundo) => ({ frente, fundo, minimo: TEXTO })),
  ),
  // Campo: o que se digita e o placeholder.
  { frente: 'text', fundo: 'fundoCampo', minimo: TEXTO },
  { frente: 'muted', fundo: 'fundoCampo', minimo: TEXTO },
  // Botão primário, parado e sob o ponteiro; e o branco puro do botão de perigo.
  { frente: 'onAccent', fundo: 'accent', minimo: TEXTO },
  { frente: 'onAccent', fundo: 'accentHover', minimo: TEXTO },
  { frente: '#ffffff', fundo: 'danger', minimo: TEXTO },
  // Pílulas e chips: a cor do papel sobre o próprio fundo translúcido, no cartão.
  { frente: 'accent', fundo: 'accentSoft', sobre: 'surface', minimo: TEXTO },
  { frente: 'text', fundo: 'accentSoft', sobre: 'surface', minimo: TEXTO },
  { frente: 'muted', fundo: 'mutedSoft', sobre: 'surface', minimo: TEXTO },
  { frente: 'ok', fundo: 'okSoft', sobre: 'surface', minimo: TEXTO },
  { frente: 'warn', fundo: 'warnSoft', sobre: 'surface', minimo: TEXTO },
  // Borda de chip, campo e opção.
  ...(['bg', 'surface', 'surface2', 'fundoCampo'] as const).map((fundo) => ({
    frente: 'borderControl' as const,
    fundo,
    minimo: NAO_TEXTO,
  })),
  // Anel de foco e anel do dia perdoado no heatmap.
  ...FUNDOS.map((fundo) => ({ frente: 'focus' as const, fundo, minimo: NAO_TEXTO })),
  ...FUNDOS.map((fundo) => ({ frente: 'frozen' as const, fundo, minimo: NAO_TEXTO })),
];

function razao(cores: Cores, par: Par): number {
  const frente = par.frente === '#ffffff' ? par.frente : cores[par.frente];
  let fundo = cores[par.fundo];
  if (par.sobre) {
    const [r, g, b] = compor(fundo, cores[par.sobre]);
    fundo = `rgb(${r} ${g} ${b})`;
  }
  return razaoDeContraste(frente, fundo);
}

describe('razão de contraste', () => {
  it('confere com valores conhecidos da WCAG', () => {
    assert.equal(razaoDeContraste('#000000', '#ffffff').toFixed(2), '21.00');
    assert.equal(razaoDeContraste('#ffffff', '#ffffff').toFixed(2), '1.00');
    assert.equal(razaoDeContraste('#777777', '#ffffff').toFixed(2), '4.48');
  });

  it('compõe a cor translúcida sobre o fundo antes da conta', () => {
    assert.deepEqual(compor('rgba(0, 0, 0, 0.5)', '#ffffff'), [127.5, 127.5, 127.5, 1]);
    assert.deepEqual(compor('rgb(0 0 0 / 50%)', '#ffffff'), [127.5, 127.5, 127.5, 1]);
  });
});

for (const nome of Object.keys(temas) as NomeDoTema[]) {
  describe(`tema ${nome}: pares obrigatórios da #181`, () => {
    for (const par of PARES) {
      const rotulo = `${par.frente} sobre ${par.fundo}${par.sobre ? ` (em ${par.sobre})` : ''}`;
      it(`${rotulo} ≥ ${par.minimo}:1`, () => {
        const r = razao(temas[nome], par);
        assert.ok(r >= par.minimo, `${rotulo} no tema ${nome}: ${r.toFixed(2)}:1`);
      });
    }
  });
}
