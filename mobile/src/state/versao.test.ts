import { abaixoDaMinima } from './versao';

describe('abaixoDaMinima', () => {
  it.each([
    ['1.9.0', '1.10.0', true],
    ['1.10.0', '1.9.0', false],
    ['1.2.0', '1.2.0', false],
    ['1.2', '1.2.1', true],
    ['2.0.0', '1.99.99', false],
  ])('%s contra mínima %s → %s', (atual, minima, esperado) => {
    expect(abaixoDaMinima(atual, minima)).toBe(esperado);
  });

  it('sem mínima, sem versão ou com valor ilegível, não trava o app', () => {
    expect(abaixoDaMinima('1.0.0', null)).toBe(false);
    expect(abaixoDaMinima(undefined, '1.0.0')).toBe(false);
    expect(abaixoDaMinima('1.0.0', 'beta')).toBe(false);
  });
});
