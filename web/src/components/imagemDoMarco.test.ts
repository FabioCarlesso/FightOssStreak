import { describe, expect, it } from 'vitest';
import { comporImagemDoMarco, LADO_DA_IMAGEM } from './imagemDoMarco.ts';

/**
 * O conteúdo da imagem do marco (#106). O que importa é o que ela diz — streak, marco e nome do
 * app — e o que ela não diz: nenhum texto além desses, e nenhuma URL a buscar para compor.
 */
function textos(streak: number, marco: number): string[] {
  return comporImagemDoMarco(streak, marco).elementos.flatMap((el) =>
    el.tipo === 'texto' ? [el.texto] : [],
  );
}

describe('composição da imagem do marco', () => {
  it('traz o streak atual, o marco e o nome do app', () => {
    const t = textos(30, 30);

    expect(t).toContain('30');
    expect(t).toContain('dias seguidos');
    expect(t).toContain('Marco de 30 dias seguidos');
    expect(t).toContain('FightOssStreak');
  });

  it('não traz nada além disso — nem arroba, nem endereço, nem número que não seja o streak', () => {
    const t = textos(100, 100);

    expect(t).toEqual([
      'FightOssStreak',
      '100',
      'dias seguidos',
      'Marco de 100 dias seguidos',
      'FOS',
    ]);
    expect(JSON.stringify(comporImagemDoMarco(100, 100))).not.toMatch(/@|https?:|url\(/i);
  });

  it('é quadrada e o arquivo é um PNG com o marco no nome', () => {
    const imagem = comporImagemDoMarco(7, 7);

    expect(imagem.largura).toBe(LADO_DA_IMAGEM);
    expect(imagem.altura).toBe(LADO_DA_IMAGEM);
    expect(imagem.nomeDoArquivo).toBe('fos-marco-7-dias.png');
  });

  it('todo elemento cabe dentro da imagem', () => {
    for (const el of comporImagemDoMarco(365, 365).elementos) {
      expect(el.x).toBeGreaterThanOrEqual(0);
      expect(el.x).toBeLessThanOrEqual(LADO_DA_IMAGEM);
      expect(el.y).toBeGreaterThanOrEqual(0);
      expect(el.y).toBeLessThanOrEqual(LADO_DA_IMAGEM);
    }
  });
});
