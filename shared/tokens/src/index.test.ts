import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { cores, espacoPx, foco, nomeCss, px, textoPx, variaveisCss } from './index.ts';

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

  it('expõe foco, fonte e sombra no CSS, com o foco valendo o accent (#186)', () => {
    const css = new Map(variaveisCss());
    assert.equal(css.get('--focus'), cores.accent);
    assert.equal(foco.focus, cores.accent);
    assert.ok(css.get('--font-sans')?.endsWith('sans-serif'));
    assert.ok(css.get('--font-mono')?.endsWith('monospace'));
    assert.equal(css.get('--shadow-device'), '0 18px 40px var(--shadow-strong)');
    assert.equal(css.get('--shadow-print'), '0 12px 30px var(--shadow)');
  });

  it('converte rem em px inteiro para o app', () => {
    assert.equal(px(1), 16);
    assert.equal(px(0.95), 15);
    assert.equal(textoPx.textXs, 13);
    assert.equal(espacoPx.space4, 16);
    assert.equal(espacoPx.space5, 24);
  });
});
