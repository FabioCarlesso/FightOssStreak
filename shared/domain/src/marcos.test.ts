import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { STREAK_MILESTONES, streakMilestoneToday } from './marcos.ts';

describe('marcos de streak', () => {
  it('reconhece cada marco no dia em que o treino de hoje o completa', () => {
    for (const marco of [7, 30, 100, 365]) {
      assert.equal(streakMilestoneToday(marco, true), marco);
    }
  });

  it('os vizinhos dos marcos não são marco', () => {
    for (const dias of [0, 1, 6, 8, 29, 31, 99, 101, 364, 366]) {
      assert.equal(streakMilestoneToday(dias, true), null);
    }
  });

  it('sem registro hoje o marco é de ontem e não aparece', () => {
    // O streak ancora em ontem até o dia acabar: 7 sem treino hoje foi completado ontem.
    assert.equal(streakMilestoneToday(7, false), null);
    assert.equal(streakMilestoneToday(30, false), null);
  });

  it('a lista é a da issue, em ordem crescente', () => {
    assert.deepEqual([...STREAK_MILESTONES], [7, 30, 100, 365]);
  });
});
