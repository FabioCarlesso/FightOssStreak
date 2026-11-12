import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  ATTIRE_LABELS,
  countClassesSince,
  graduationForm,
  graduationLines,
  kindCountsAsClass,
  toGraduationGoalBody,
  kindRecordsAttire,
} from './graduacao.ts';

describe('próximo grau', () => {
  it('só o que acontece no tatame registra kimono', () => {
    for (const kind of ['AULA', 'DRILL', 'ROLA', 'OUTRO']) {
      assert.equal(kindRecordsAttire(kind), true, kind);
    }
    assert.equal(kindRecordsAttire('FISICO'), false);
    assert.equal(kindRecordsAttire('DESCANSO'), false);
  });

  it('só AULA conta como aula', () => {
    assert.equal(kindCountsAsClass('AULA'), true);
    for (const kind of ['DRILL', 'ROLA', 'OUTRO', 'FISICO', 'DESCANSO']) {
      assert.equal(kindCountsAsClass(kind), false, kind);
    }
  });

  it('conta as aulas desde o último grau, inclusive o próprio dia, por kimono', () => {
    // O mesmo cenário do GraduationIntegrationTest, no backend: as duas regras dizem o mesmo.
    const contagem = countClassesSince(
      [
        { trainedOn: '2026-07-31', kind: 'AULA', attire: 'GI' },
        { trainedOn: '2026-08-01', kind: 'AULA', attire: 'GI' },
        { trainedOn: '2026-08-05', kind: 'AULA', attire: 'NOGI' },
        { trainedOn: '2026-08-10', kind: 'AULA', attire: null },
        { trainedOn: '2026-08-11', kind: 'ROLA', attire: 'GI' },
        { trainedOn: '2026-08-12', kind: 'DRILL', attire: 'NOGI' },
        { trainedOn: '2026-08-13', kind: 'FISICO' },
        { trainedOn: '2026-08-14', kind: 'DESCANSO' },
      ],
      '2026-08-01',
    );
    assert.deepEqual(contagem, { total: 3, gi: 1, nogi: 1, unspecified: 1 });
  });

  it('sem sessões, tudo zero', () => {
    assert.deepEqual(countClassesSince([], '2026-08-01'), {
      total: 0,
      gi: 0,
      nogi: 0,
      unspecified: 0,
    });
  });

  it('cada linha diz feito, exigido e quanto falta, sem passar de zero', () => {
    const linhas = graduationLines(
      { minTotal: 40, minGi: 30, minNogi: 10 },
      { total: 42, gi: 35, nogi: 7, unspecified: 0 },
    );
    assert.deepEqual(linhas.total, { done: 42, minimum: 40, remaining: 0 });
    assert.deepEqual(linhas.gi, { done: 35, minimum: 30, remaining: 0 });
    assert.deepEqual(linhas.nogi, { done: 7, minimum: 10, remaining: 3 });
  });

  it('linha sem mínimo só conta', () => {
    const linhas = graduationLines({ minTotal: 40 }, { total: 5, gi: 2, nogi: 1, unspecified: 2 });
    assert.deepEqual(linhas.gi, { done: 2, minimum: null, remaining: null });
    assert.deepEqual(linhas.nogi, { done: 1, minimum: null, remaining: null });
  });

  it('com kimono vem primeiro', () => {
    assert.deepEqual(
      ATTIRE_LABELS.map((option) => option.value),
      ['GI', 'NOGI'],
    );
  });

  it('o formulário vazio só manda a data, e campo vazio não vira zero', () => {
    const corpo = toGraduationGoalBody({ ...graduationForm(null), lastGraduationOn: '2026-08-01' });
    assert.deepEqual(corpo, {
      lastGraduationOn: '2026-08-01',
      currentLabel: undefined,
      nextLabel: undefined,
      minTotal: undefined,
      minGi: undefined,
      minNogi: undefined,
    });
  });

  it('ida e volta entre meta gravada e formulário preserva os números, inclusive zero', () => {
    const meta = {
      lastGraduationOn: '2026-08-01',
      currentLabel: 'branca',
      nextLabel: 'branca 1º grau',
      minTotal: 40,
      minGi: 30,
      minNogi: 0,
    };
    assert.deepEqual(toGraduationGoalBody(graduationForm(meta)), meta);
  });

  it('número que não é inteiro não vai: o backend recusaria, e a tela não oferece', () => {
    const base = { ...graduationForm(null), lastGraduationOn: '2026-08-01' };
    assert.equal(toGraduationGoalBody({ ...base, minTotal: '4,5' }).minTotal, undefined);
    assert.equal(toGraduationGoalBody({ ...base, minTotal: '-1' }).minTotal, undefined);
    assert.equal(toGraduationGoalBody({ ...base, minTotal: ' 40 ' }).minTotal, 40);
  });
});
