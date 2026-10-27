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
});
