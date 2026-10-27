import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { espacoPx, nomeCss, px, textoPx, variaveisCss } from './index.ts';

describe('tokens', () => {
  it('converte a chave camelCase no nome da variável CSS do contrato', () => {
    assert.equal(nomeCss('surface2'), 'surface-2');
    assert.equal(nomeCss('borderControl'), 'border-control');
    assert.equal(nomeCss('text2xs'), 'text-2xs');
    assert.equal(nomeCss('text2xl'), 'text-2xl');
    assert.equal(nomeCss('heat1'), 'heat-1');
    assert.equal(nomeCss('radiusPill'), 'radius-pill');
    assert.equal(nomeCss('bg'), 'bg');
  });

  it('não tem duas variáveis com o mesmo nome', () => {
    const nomes = variaveisCss().map(([nome]) => nome);
    assert.equal(new Set(nomes).size, nomes.length);
  });

  it('converte rem em px inteiro para o app', () => {
    assert.equal(px(1), 16);
    assert.equal(px(0.95), 15);
    assert.equal(textoPx.textXs, 13);
    assert.equal(espacoPx.space4, 16);
    assert.equal(espacoPx.space5, 24);
  });
});
