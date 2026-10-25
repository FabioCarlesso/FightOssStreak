import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { nextNodeToResume, type ResumeTreeModule } from './retomar.ts';

const ARVORE: ResumeTreeModule[] = [
  {
    nodes: [
      { code: 'M0.1', title: 'Etiqueta', status: 'COMPLETED', prereqCodes: [] },
      { code: 'M0.2', title: 'Quedas', status: 'AVAILABLE', prereqCodes: ['M0.1'] },
    ],
  },
  {
    nodes: [
      { code: 'M1.1', title: 'Fuga de montada', status: 'IN_PROGRESS', prereqCodes: ['M0.1'] },
      { code: 'M1.2', title: 'Fuga de 100kg', status: 'LOCKED', prereqCodes: ['M0.2'] },
    ],
  },
];

describe('retomar de onde parei', () => {
  it('revisão vencida vem primeiro, e é a primeira da agenda — sem reordenar', () => {
    const due = [
      { nodeCode: 'M0.1', title: 'Etiqueta' },
      { nodeCode: 'M0.3', title: 'Tap' },
    ];
    assert.deepEqual(nextNodeToResume(due, ARVORE), {
      code: 'M0.1',
      title: 'Etiqueta',
      reason: 'REVISAO',
    });
  });

  it('com revisão vencida, nem precisa da árvore', () => {
    assert.equal(
      nextNodeToResume([{ nodeCode: 'M0.1', title: 'Etiqueta' }], [])?.reason,
      'REVISAO',
    );
  });

  it('sem nada vencido, o nó em andamento vem antes do liberado, mesmo depois na árvore', () => {
    assert.deepEqual(nextNodeToResume([], ARVORE), {
      code: 'M1.1',
      title: 'Fuga de montada',
      reason: 'EM_ANDAMENTO',
    });
  });

  it('só nós novos elegíveis: o primeiro na ordem da árvore', () => {
    const arvore: ResumeTreeModule[] = [
      {
        nodes: [
          { code: 'M0.1', status: 'COMPLETED', prereqCodes: [] },
          { code: 'M0.2', title: 'Quedas', status: 'AVAILABLE', prereqCodes: ['M0.1'] },
          { code: 'M0.3', title: 'Tap', status: 'AVAILABLE', prereqCodes: [] },
        ],
      },
    ];
    assert.deepEqual(nextNodeToResume([], arvore), {
      code: 'M0.2',
      title: 'Quedas',
      reason: 'NOVO',
    });
  });

  it('nunca sugere nó travado, nem se o status vier liberado com pré-requisito pendente', () => {
    const arvore: ResumeTreeModule[] = [
      {
        nodes: [
          { code: 'M0.1', status: 'AVAILABLE', prereqCodes: [] },
          { code: 'M0.2', status: 'LOCKED', prereqCodes: ['M0.1'] },
          // Divergência hipotética entre status e grafo: a regra do grafo vence.
          { code: 'M0.3', status: 'IN_PROGRESS', prereqCodes: ['M0.2'] },
        ],
      },
    ];
    assert.equal(nextNodeToResume([], arvore)?.code, 'M0.1');
  });

  it('respeita a regra ANY: basta um pré-requisito concluído', () => {
    const arvore: ResumeTreeModule[] = [
      {
        nodes: [
          { code: 'A', status: 'COMPLETED', prereqCodes: [] },
          { code: 'B', status: 'LOCKED', prereqCodes: ['X'] },
          { code: 'C', status: 'AVAILABLE', unlockRule: 'ANY', prereqCodes: ['A', 'B'] },
        ],
      },
    ];
    assert.equal(nextNodeToResume([], arvore)?.code, 'C');
  });

  it('nada vencido e tudo concluído: tudo em dia', () => {
    const arvore: ResumeTreeModule[] = [
      { nodes: [{ code: 'M0.1', status: 'COMPLETED', prereqCodes: [] }] },
    ];
    assert.equal(nextNodeToResume([], arvore), null);
  });

  it('título ausente cai para o código, para o atalho nunca sair vazio', () => {
    assert.equal(nextNodeToResume([{ nodeCode: 'M0.9' }], [])?.title, 'M0.9');
  });
});
