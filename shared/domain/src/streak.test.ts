import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  activeDaysInWindow,
  addDays,
  currentStreak,
  daysBetween,
  longestStreak,
  resolveStreakWithFreeze,
} from './streak.ts';

const TODAY = '2026-08-16';

describe('streak', () => {
  it('sem registro nenhum, streak é zero', () => {
    assert.equal(currentStreak([], TODAY), 0);
    assert.equal(longestStreak([]), 0);
  });

  it('dias consecutivos terminando hoje contam integralmente', () => {
    assert.equal(currentStreak([TODAY, '2026-08-15', '2026-08-14'], TODAY), 3);
  });

  it('não quebra durante o dia em que ainda não se treinou', () => {
    // Mesmo caso de StreakServiceTest.streakSurvivesUntilEndOfDay.
    assert.equal(currentStreak(['2026-08-15', '2026-08-14'], TODAY), 2);
  });

  it('dois dias sem registro quebram o streak', () => {
    assert.equal(currentStreak(['2026-08-14', '2026-08-13'], TODAY), 0);
  });

  it('datas repetidas contam uma vez só', () => {
    assert.equal(currentStreak([TODAY, TODAY, '2026-08-15'], TODAY), 2);
  });

  it('o recorde olha o histórico inteiro, não só a sequência atual', () => {
    const days = [TODAY, '2026-08-06', '2026-08-05', '2026-08-04', '2026-08-03'];
    assert.equal(currentStreak(days, TODAY), 1);
    assert.equal(longestStreak(days), 4);
  });

  it('dias ativos na janela de 30 dias ignoram registros mais antigos', () => {
    const days = [TODAY, '2026-07-18', '2026-07-17', '2026-06-17'];
    assert.equal(activeDaysInWindow(days, TODAY, 30), 2);
  });
});

describe('aritmética de calendário', () => {
  it('soma dias atravessando a virada de mês', () => {
    assert.equal(addDays('2026-08-31', 1), '2026-09-01');
    assert.equal(addDays('2026-01-01', -1), '2025-12-31');
  });

  it('atravessa 29 de fevereiro em ano bissexto', () => {
    assert.equal(addDays('2028-02-28', 1), '2028-02-29');
  });

  it('mede a diferença entre datas', () => {
    assert.equal(daysBetween('2026-08-10', TODAY), 6);
    assert.equal(daysBetween(TODAY, TODAY), 0);
  });

  it('rejeita data fora do formato', () => {
    assert.throws(() => addDays('16/08/2026', 1), /YYYY-MM-DD/);
  });
});

describe('freeze de streak', () => {
  const FREEZES = 2;

  it('um dia perdido com saldo mantém a corrente e cobra um freeze', () => {
    // Treinou 14 e 16; faltou dia 15. Sem freeze a corrente valeria 1.
    const result = resolveStreakWithFreeze([TODAY, '2026-08-14', '2026-08-13'], TODAY, [], FREEZES);

    assert.equal(result.currentStreak, 3);
    assert.deepEqual(result.frozenDays, ['2026-08-15']);
    assert.deepEqual(result.newlyFrozenDays, ['2026-08-15']);
    assert.equal(result.freezesUsedThisMonth, 1);
    assert.equal(result.freezesRemaining, 1);
  });

  it('sem saldo o comportamento é o de antes: a corrente para no buraco', () => {
    const gasto = ['2026-08-15', '2026-08-12'];
    // Faltaram 15, 12 (já perdoados) e 10 — para o terceiro não há mais saldo no mês.
    const days = [TODAY, '2026-08-14', '2026-08-13', '2026-08-11', '2026-08-09'];

    const result = resolveStreakWithFreeze(days, TODAY, gasto, FREEZES);

    assert.equal(result.currentStreak, 4);
    assert.deepEqual(result.newlyFrozenDays, []);
    assert.equal(result.freezesRemaining, 0);
  });

  it('com saldo zerado nada é perdoado e a corrente vale o que valia', () => {
    const result = resolveStreakWithFreeze([TODAY, '2026-08-14'], TODAY, [], 0);

    assert.equal(result.currentStreak, 1);
    assert.deepEqual(result.frozenDays, []);
    assert.equal(result.freezesRemaining, 0);
  });

  it('o saldo renova no mês seguinte, e o mês do dia perdido é quem paga', () => {
    const setembro = '2026-09-02';
    // Os dois freezes de agosto já foram gastos; o buraco de 1/9 é cobrado de setembro.
    const result = resolveStreakWithFreeze(
      [setembro, '2026-08-31', '2026-08-30'],
      setembro,
      ['2026-08-15', '2026-08-12'],
      FREEZES,
    );

    assert.equal(result.currentStreak, 3);
    assert.deepEqual(result.frozenDays, ['2026-09-01']);
    assert.equal(result.freezesUsedThisMonth, 1);
    assert.equal(result.freezesRemaining, 1);
  });

  it('recalcular não cobra de novo o dia que já está no histórico', () => {
    const days = [TODAY, '2026-08-14', '2026-08-13'];
    const primeira = resolveStreakWithFreeze(days, TODAY, [], FREEZES);
    const segunda = resolveStreakWithFreeze(days, TODAY, primeira.newlyFrozenDays, FREEZES);

    assert.deepEqual(segunda.newlyFrozenDays, []);
    assert.equal(segunda.currentStreak, primeira.currentStreak);
    assert.equal(segunda.freezesUsedThisMonth, 1);
  });

  it('ontem sem treino é perdoado, e hoje nunca gasta freeze', () => {
    // Ainda não treinou hoje: o dia não acabou e não pode ser cobrado.
    const result = resolveStreakWithFreeze(['2026-08-14', '2026-08-13'], TODAY, [], FREEZES);

    assert.equal(result.currentStreak, 2);
    assert.deepEqual(result.frozenDays, ['2026-08-15']);
    assert.equal(result.freezesUsedThisMonth, 1);
  });

  it('não perdoa dias anteriores ao primeiro treino', () => {
    // Único registro é o de anteontem: ontem é perdoado, e o resto do mês não é dívida de ninguém.
    const result = resolveStreakWithFreeze(['2026-08-14'], TODAY, [], FREEZES);

    assert.equal(result.currentStreak, 1);
    assert.deepEqual(result.frozenDays, ['2026-08-15']);
  });

  it('sem registro nenhum não há corrente nem freeze gasto', () => {
    const result = resolveStreakWithFreeze([], TODAY, [], FREEZES);

    assert.equal(result.currentStreak, 0);
    assert.deepEqual(result.frozenDays, []);
    assert.equal(result.freezesRemaining, FREEZES);
  });

  it('dia perdoado que ganha registro depois devolve o freeze', () => {
    // `drilledOn` permite registrar o treino de ontem, e é caminho normal do app. Cobrar por um
    // dia que acabou tendo treino faria abrir a home de manhã custar saldo.
    const result = resolveStreakWithFreeze(
      [TODAY, '2026-08-15', '2026-08-14'],
      TODAY,
      ['2026-08-15'],
      FREEZES,
    );

    assert.equal(result.currentStreak, 3);
    assert.deepEqual(result.releasedDays, ['2026-08-15']);
    assert.deepEqual(result.frozenDays, []);
    assert.equal(result.freezesUsedThisMonth, 0);
    assert.equal(result.freezesRemaining, FREEZES);
  });

  it('freeze gasto continua gasto depois que a corrente quebrou', () => {
    // A corrente morreu (dois dias sem registro), mas o freeze de 15/08 não volta ao saldo.
    const result = resolveStreakWithFreeze(['2026-08-10'], TODAY, ['2026-08-15'], FREEZES);

    assert.equal(result.currentStreak, 0);
    assert.equal(result.freezesUsedThisMonth, 1);
    assert.equal(result.freezesRemaining, 1);
  });
});
