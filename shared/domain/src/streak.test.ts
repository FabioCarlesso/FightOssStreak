import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  activeDaysInWindow,
  addDays,
  currentStreak,
  daysBetween,
  longestStreak,
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
