import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { buildStreakHeatmap, heatLevel, weekdayOf, type ActivityDay } from './heatmap.ts';

/**
 * A grade do heatmap (#102).
 *
 * O que se testa aqui é calendário, e é onde a conta escorrega: coluna que não fecha no domingo,
 * dia futuro pintado como falta e lacuna que some da grade. A regra de quais dias contam é a do
 * streak, e continua testada em `streak.test.ts` — aqui ela chega pronta.
 */

// 2026-08-16 é um domingo. Escolhido de propósito: é o mesmo "hoje" dos testes do backend.
const TODAY = '2026-08-16';

function dia(day: string, count: number, frozen = false): ActivityDay {
  return { day, count, frozen };
}

function nivelPorDia(registros: readonly ActivityDay[], semanas: number): Map<string, number> {
  return new Map(
    buildStreakHeatmap(registros, TODAY, semanas)
      .weeks.flat()
      .filter((celula) => celula !== null)
      .map((celula) => [celula.day, celula.level]),
  );
}

describe('dia da semana', () => {
  it('não passa por fuso: a data é de calendário', () => {
    assert.equal(weekdayOf('2026-08-16'), 0);
    assert.equal(weekdayOf('2026-08-22'), 6);
  });
});

describe('degraus de intensidade', () => {
  it('vazio, um, dois e daí em diante o teto', () => {
    assert.equal(heatLevel(0), 0);
    assert.equal(heatLevel(1), 1);
    assert.equal(heatLevel(2), 2);
    assert.equal(heatLevel(3), 3);
    assert.equal(heatLevel(9), 3);
  });
});

describe('grade do heatmap', () => {
  it('tem uma coluna de sete dias por semana pedida, começando num domingo', () => {
    const mapa = buildStreakHeatmap([], TODAY, 4);

    assert.equal(mapa.weeks.length, 4);
    assert.ok(mapa.weeks.every((semana) => semana.length === 7));
    assert.equal(weekdayOf(mapa.from), 0);
    assert.equal(mapa.from, '2026-07-26');
    assert.equal(mapa.to, TODAY);
  });

  it('dia futuro fica vazio em vez de virar falta', () => {
    const ultima = buildStreakHeatmap([], TODAY, 2).weeks.at(-1)!;

    // Hoje é domingo: a semana corrente só tem o primeiro dia preenchido.
    assert.equal(ultima[0]?.day, TODAY);
    assert.ok(ultima.slice(1).every((celula) => celula === null));
  });

  it('separa dia com registro de dia sem registro, e guarda a intensidade', () => {
    const registros = [dia('2026-08-14', 1), dia('2026-08-15', 3)];
    const niveis = nivelPorDia(registros, 3);

    assert.equal(niveis.get('2026-08-14'), 1);
    assert.equal(niveis.get('2026-08-15'), 3);
    assert.equal(niveis.get('2026-08-13'), 0);
    assert.equal(buildStreakHeatmap(registros, TODAY, 3).activeDays, 2);
  });

  it('mostra o histórico bruto: corrente quebrada e retomada aparecem com a lacuna no meio', () => {
    const registros = [
      dia('2026-08-01', 1),
      dia('2026-08-02', 1),
      // seis dias de nada — a corrente morreu aqui
      dia('2026-08-09', 1),
      dia('2026-08-10', 1),
    ];
    const niveis = nivelPorDia(registros, 4);

    assert.equal(niveis.get('2026-08-02'), 1);
    assert.equal(niveis.get('2026-08-05'), 0);
    assert.equal(niveis.get('2026-08-09'), 1);
    assert.equal(buildStreakHeatmap(registros, TODAY, 4).activeDays, 4);
  });

  it('dia perdoado por freeze é marcado sem virar dia de treino', () => {
    const mapa = buildStreakHeatmap([dia('2026-08-14', 0, true), dia('2026-08-15', 1)], TODAY, 3);
    const perdoado = mapa.weeks.flat().find((celula) => celula?.day === '2026-08-14');

    assert.equal(perdoado?.frozen, true);
    assert.equal(perdoado?.level, 0);
    assert.equal(mapa.frozenDays, 1);
    // Dia coberto mantém a corrente e NÃO conta como dia de treino (D55).
    assert.equal(mapa.activeDays, 1);
  });

  it('registro anterior ao período não entra na contagem da grade', () => {
    const mapa = buildStreakHeatmap([dia('2026-01-05', 2), dia('2026-08-16', 1)], TODAY, 2);

    assert.equal(mapa.activeDays, 1);
    assert.ok(!mapa.weeks.flat().some((celula) => celula?.day === '2026-01-05'));
  });
});
