import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { parseRequirements, requirementStatus, suggestTechniques } from './requisitos.ts';

// Os mesmos casos de GraduationRequirementServiceTest (D17): se um lado mudar sem o outro, um dos
// dois reprova. A lista é fictícia — nunca trecho de manual de academia.
describe('parseRequirements', () => {
  it('tira marcadores e linhas vazias, sem confundir número com marcador', () => {
    assert.deepEqual(
      parseRequirements(
        '• Fuga de montada\r\n\r\n  - 2. Armlock\n1) Guarda\n100kg: manutenção\n' +
          '(3)  Tempo   de faixa\na) Etiqueta\n1.5 metros de distância\n* * Duplo marcador',
      ),
      [
        'Fuga de montada',
        'Armlock',
        'Guarda',
        '100kg: manutenção',
        'Tempo de faixa',
        'Etiqueta',
        '1.5 metros de distância',
        'Duplo marcador',
      ],
    );
    assert.deepEqual(parseRequirements('\n - \n•\n'), []);
  });

  it('linha acima do teto lança apontando a linha, e não é cortada', () => {
    assert.throws(() => parseRequirements(`ok\n${'x'.repeat(201)}`), /linha 2/);
  });
});

describe('requirementStatus', () => {
  it('sem nó, vale a marcação à mão', () => {
    assert.equal(requirementStatus([], false), 'PENDENTE');
    assert.equal(requirementStatus([], true), 'CONCLUIDO');
  });

  it('com nó, a marcação à mão não conta', () => {
    assert.equal(requirementStatus([null, null], true), 'PENDENTE');
  });

  it('derivado do progresso gravado', () => {
    assert.equal(requirementStatus(['IN_PROGRESS', null], false), 'EM_ANDAMENTO');
    assert.equal(requirementStatus(['COMPLETED', undefined], false), 'EM_ANDAMENTO');
    assert.equal(requirementStatus(['COMPLETED', 'COMPLETED'], false), 'CONCLUIDO');
  });
});

describe('suggestTechniques', () => {
  const positions = [
    { code: 'CEM_QUILOS', name: '100kg', aliases: ['lateral', 'side control'] },
    { code: 'MONTADA', name: 'Montada', aliases: ['mount'] },
  ];
  const nodes = [
    { code: 'M1.3', title: 'Fuga de 100kg (side control)', positionCodes: ['CEM_QUILOS'] },
    {
      code: 'M1.4',
      title: 'Fuga de montada: upa (bridge) e elbow escape',
      positionCodes: ['MONTADA'],
    },
    {
      code: 'M4.6',
      title: 'Armlock da montada',
      positionCodes: ['MONTADA'],
      aliases: ['armbar da montada'],
    },
    {
      code: 'M7.1',
      title: 'Kimura: da guarda, do 100kg e das costas',
      positionCodes: ['CEM_QUILOS'],
      aliases: ['chave de ombro'],
    },
  ];
  const codes = (list: Array<{ code?: string }>) => list.map((n) => n.code);

  it('ordena por palavras em comum, título antes de posição', () => {
    assert.deepEqual(codes(suggestTechniques(nodes, 'Saída da montada com upa', positions)), [
      'M1.4',
      'M4.6',
    ]);
    assert.deepEqual(codes(suggestTechniques(nodes, 'Kimura do 100kg', positions)), [
      'M7.1',
      'M1.3',
    ]);
  });

  it('acha pelo apelido do nó, sem acento nem caixa', () => {
    assert.deepEqual(codes(suggestTechniques(nodes, 'CHAVE DE OMBRO', positions)), ['M7.1']);
  });

  it('requisito sem técnica correspondente não sugere nada', () => {
    assert.deepEqual(suggestTechniques(nodes, 'Tempo mínimo de faixa', positions), []);
    assert.deepEqual(suggestTechniques(nodes, 'de da do e', positions), []);
  });

  it('respeita o limite', () => {
    assert.equal(suggestTechniques(nodes, 'montada 100kg', positions, 2).length, 2);
  });
});
