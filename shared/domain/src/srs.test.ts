import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  DEFAULT_EASE_FACTOR,
  MIN_EASE_FACTOR,
  type SrsState,
  initialSchedule,
  isDue,
  review,
} from './srs.ts';
import { addDays } from './streak.ts';

const TODAY = '2026-08-16';

/**
 * Os casos abaixo são os mesmos de SrsSchedulerTest.java. Se um lado mudar sem o outro, um dos
 * dois conjuntos quebra — que é exatamente o alarme desejado.
 */
describe('agendamento SM-2', () => {
  it('concluir um nó agenda o primeiro drill para o dia seguinte', () => {
    const state = initialSchedule(TODAY);
    assert.equal(state.nextReviewOn, '2026-08-17');
    assert.equal(state.repetitions, 0);
    assert.equal(state.easeFactor, DEFAULT_EASE_FACTOR);
  });

  it('os intervalos crescem 2 → 5 → escalados pelo fator de facilidade', () => {
    const first = review(null, 'OK', TODAY);
    assert.equal(first.intervalDays, 2);
    assert.equal(first.repetitions, 1);

    const second = review(first, 'OK', '2026-08-18');
    assert.equal(second.intervalDays, 5);

    // Terceira revisão em diante o intervalo escala: round(5 * 2.5) = 13 dias.
    const third = review(second, 'OK', '2026-08-23');
    assert.equal(third.intervalDays, 13);
    assert.equal(third.nextReviewOn, addDays('2026-08-23', third.intervalDays));
  });

  it('esquecer zera as repetições e traz a técnica de volta amanhã', () => {
    const grown = review(review(null, 'EASY', TODAY), 'EASY', '2026-08-18');
    const lapsed = review(grown, 'FORGOT', '2026-08-23');

    assert.equal(lapsed.repetitions, 0);
    assert.equal(lapsed.intervalDays, 1);
    assert.equal(lapsed.nextReviewOn, '2026-08-24');
  });

  it('drills difíceis reduzem o fator de facilidade; fáceis aumentam', () => {
    assert.ok(review(null, 'HARD', TODAY).easeFactor < DEFAULT_EASE_FACTOR);
    assert.ok(review(null, 'EASY', TODAY).easeFactor > DEFAULT_EASE_FACTOR);
  });

  it('o fator de facilidade nunca cai abaixo do piso do SM-2', () => {
    let state: SrsState = review(null, 'HARD', TODAY);
    for (let i = 0; i < 20; i += 1) {
      state = review(state, 'HARD', state.nextReviewOn);
      assert.ok(state.easeFactor >= MIN_EASE_FACTOR);
      assert.ok(state.intervalDays >= 1);
    }
  });

  it('reconhece nó vencido e nó ainda no prazo', () => {
    assert.equal(isDue({ nextReviewOn: '2026-08-10' }, TODAY), true);
    assert.equal(isDue({ nextReviewOn: TODAY }, TODAY), true);
    assert.equal(isDue({ nextReviewOn: '2026-08-20' }, TODAY), false);
  });
});
