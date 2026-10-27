import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { estaAtualizado, gerarCss } from './gen-tokens.mjs';

describe('web/src/tokens.css', () => {
  it('bate com shared/tokens (rode `npm run gen:tokens` se falhar)', () => {
    assert.ok(
      estaAtualizado(),
      'web/src/tokens.css está desatualizado em relação a shared/tokens — rode `npm run gen:tokens`.',
    );
  });

  it('diz no cabeçalho que é gerado e como regenerar', () => {
    const css = gerarCss();
    assert.match(css, /GERADO/);
    assert.match(css, /npm run gen:tokens/);
  });

  it('mantém o tema escuro e os tokens do contrato', () => {
    const css = gerarCss();
    assert.match(css, /color-scheme: dark;/);
    assert.match(css, /--surface-2: #1e222b;/);
    assert.match(css, /--text-2xs: 0\.7rem;/);
    assert.match(css, /--space-6: 2\.5rem;/);
    assert.match(css, /--radius-pill: 999px;/);
  });

  it('escreve o tema claro para o sistema e para a escolha manual (D74)', () => {
    const css = gerarCss();
    const [escuro, resto] = css.split('@media (prefers-color-scheme: light)');
    assert.ok(resto, 'falta o bloco do tema claro pelo sistema');
    assert.match(escuro, /:root \{\n {2}color-scheme: dark;/);
    assert.match(resto, /:root:not\(\[data-theme='dark'\]\) \{\n {4}color-scheme: light;/);
    assert.match(resto, /\n\[data-theme='light'\] \{\n {2}color-scheme: light;/);
    // Os dois blocos claros trazem o mesmo valor, e nenhum repete o que é fixo por natureza.
    assert.equal(resto.match(/--bg: #f5f6f8;/g)?.length, 2);
    assert.equal(resto.match(/--accent: #a04516;/g)?.length, 2);
    assert.doesNotMatch(resto, /--belt-|--phone-frame|--shadow|--text-xs|--radius/);
  });
});
