import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  type GraphNode,
  type ProgressStatus,
  isUnlocked,
  missingPrereqs,
  resolveStatuses,
} from './unlock.ts';

const node = (
  code: string,
  prereqCodes: string[] = [],
  unlockRule: 'ALL' | 'ANY' = 'ALL',
): GraphNode => ({ code, prereqCodes, unlockRule });

describe('desbloqueio de nó', () => {
  it('nó sem pré-requisito está sempre aberto', () => {
    assert.equal(isUnlocked(node('M0.1'), new Set()), true);
  });

  it('regra ALL exige todos os pré-requisitos', () => {
    const m15 = node('M1.5', ['M1.3', 'M1.4']);
    assert.equal(isUnlocked(m15, new Set(['M1.3'])), false);
    assert.equal(isUnlocked(m15, new Set(['M1.3', 'M1.4'])), true);
  });

  it('regra ANY exige apenas um — qualquer passagem serve (D13)', () => {
    const m41 = node('M4.1', ['M3.2', 'M3.3'], 'ANY');
    assert.equal(isUnlocked(m41, new Set()), false);
    assert.equal(isUnlocked(m41, new Set(['M3.3'])), true);
  });

  it('lista o que falta para abrir o nó', () => {
    const m15 = node('M1.5', ['M1.3', 'M1.4']);
    assert.deepEqual(missingPrereqs(m15, new Set(['M1.3'])), ['M1.4']);
    assert.deepEqual(missingPrereqs(m15, new Set(['M1.3', 'M1.4'])), []);
  });

  it('com ANY, o que falta são alternativas — basta concluir uma', () => {
    const m41 = node('M4.1', ['M3.2', 'M3.3'], 'ANY');
    assert.deepEqual(missingPrereqs(m41, new Set()), ['M3.2', 'M3.3']);
  });

  it('bloqueio derivado sobrepõe qualquer status persistido', () => {
    const persisted = new Map<string, ProgressStatus>([['M1.1', 'IN_PROGRESS']]);
    const statuses = resolveStatuses([node('M0.3'), node('M1.1', ['M0.3'])], persisted);

    assert.equal(statuses.get('M1.1'), 'LOCKED');
  });

  it('concluir o pré-requisito libera o sucessor', () => {
    const persisted = new Map<string, ProgressStatus>([['M0.3', 'COMPLETED']]);
    const statuses = resolveStatuses([node('M0.3'), node('M1.1', ['M0.3'])], persisted);

    assert.equal(statuses.get('M0.3'), 'COMPLETED');
    assert.equal(statuses.get('M1.1'), 'AVAILABLE');
  });

  it('nó aberto e nunca tocado aparece como AVAILABLE', () => {
    const statuses = resolveStatuses([node('M0.1')], new Map());
    assert.equal(statuses.get('M0.1'), 'AVAILABLE');
  });
});
