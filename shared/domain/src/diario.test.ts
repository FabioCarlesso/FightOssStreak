import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { emptySessionFields, formatDay, monthRange, toSessionBody } from './diario.ts';
import { previewIntervalDays } from './previsao.ts';
import { review } from './srs.ts';

// Movidos do web para cá na #141, para o web e o app converterem e mostrarem do mesmo jeito.

describe('monthRange', () => {
  it('vai do dia 1 ao último dia do mês, inclusive em fevereiro bissexto', () => {
    assert.deepEqual(monthRange('2026-10-05'), { de: '2026-10-01', ate: '2026-10-31' });
    assert.deepEqual(monthRange('2028-02-10'), { de: '2028-02-01', ate: '2028-02-29' });
  });
});

describe('formatDay', () => {
  it('troca a ordem sem passar por Date, que mudaria o dia a oeste de Greenwich', () => {
    assert.equal(formatDay('2026-08-16'), '16/08/2026');
    assert.equal(formatDay(undefined), '');
  });
});

describe('toSessionBody', () => {
  it('campo vazio vira "não informado", nunca zero', () => {
    const corpo = toSessionBody(emptySessionFields('2026-10-05'));
    assert.equal(corpo.trainedOn, '2026-10-05');
    assert.equal(corpo.kind, 'AULA');
    assert.equal(corpo.durationMinutes, undefined);
    assert.equal(corpo.weightKg, undefined);
    assert.equal(corpo.learned, undefined);
  });

  it('aceita vírgula decimal no peso, como o teclado brasileiro digita', () => {
    const corpo = toSessionBody({ ...emptySessionFields('2026-10-05'), weightKg: '78,4' });
    assert.equal(corpo.weightKg, 78.4);
  });
});

describe('previewIntervalDays', () => {
  it('sem agendamento, é a primeira revisão do SM-2, a mesma regra do backend', () => {
    for (const recall of ['FORGOT', 'HARD', 'OK', 'EASY'] as const) {
      assert.equal(
        previewIntervalDays(undefined, recall, '2026-10-05'),
        review(null, recall, '2026-10-05').intervalDays,
      );
    }
  });

  it('depois de duas repetições não promete número, porque o fator de facilidade é desconhecido', () => {
    const srs = { scheduled: true, nextReviewOn: '2026-10-10', repetitions: 3, intervalDays: 15 };
    assert.equal(previewIntervalDays(srs, 'OK', '2026-10-05'), null);
  });
});
