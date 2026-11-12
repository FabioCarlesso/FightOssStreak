import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  groupPositionsByFamily,
  nodesFromPosition,
  searchKey,
  searchTechniques,
} from './posicoes.ts';

const positions = [
  { code: 'MEIA_GUARDA', name: 'Meia-guarda', aliases: ['half guard'], family: 'GUARDA' },
  {
    code: 'CEM_QUILOS',
    name: '100kg',
    aliases: ['lateral', 'side control'],
    family: 'CONTROLE_POR_CIMA',
  },
  { code: 'GUARDA_FECHADA', name: 'Guarda fechada', aliases: [], family: 'GUARDA' },
  { code: 'EM_PE', name: 'Em pé', aliases: [], family: 'EM_PE' },
];

const nodes = [
  {
    code: 'M2.5',
    title: 'Armlock da guarda (juji-gatame)',
    positionCodes: ['GUARDA_FECHADA'],
    aliases: ['chave de braço', 'armbar'],
  },
  {
    code: 'M3.6',
    title: 'Meia-guarda: conceito e enquadramento',
    positionCodes: ['MEIA_GUARDA'],
    aliases: [],
  },
  {
    code: 'M3.7',
    title: 'Raspagem de meia-guarda (old school)',
    positionCodes: ['MEIA_GUARDA'],
    aliases: [],
  },
  {
    code: 'M4.1',
    title: '100kg (side control): manutenção e pressão',
    positionCodes: ['CEM_QUILOS'],
    aliases: [],
  },
  { code: 'M4.2', title: 'Joelho na barriga (knee on belly)', positionCodes: [], aliases: [] },
  {
    code: 'M7.1',
    title: 'Kimura: da guarda, do 100kg e das costas',
    positionCodes: ['GUARDA_FECHADA', 'CEM_QUILOS'],
    aliases: ['chave de ombro'],
  },
  { code: 'M0.1', title: 'Etiqueta de tatame e hierarquia' },
];

const codes = (list: Array<{ code?: string }>) => list.map((node) => node.code);

describe('searchKey', () => {
  it('ignora acento, caixa, hífen e espaço sobrando', () => {
    assert.equal(searchKey('  Meia-Guarda '), 'meia guarda');
    assert.equal(searchKey('Em  PÉ'), 'em pe');
    assert.equal(searchKey('Chave de BRAÇO'), 'chave de braco');
  });
});

describe('searchTechniques', () => {
  it('sem texto e sem posição devolve nada', () => {
    assert.deepEqual(searchTechniques(nodes, { query: '  ' }, positions), []);
  });

  it('acha pelo apelido do nó, sem olhar acento nem caixa', () => {
    assert.deepEqual(codes(searchTechniques(nodes, { query: 'CHAVE DE BRACO' }, positions)), [
      'M2.5',
    ]);
    assert.deepEqual(codes(searchTechniques(nodes, { query: 'armbar' }, positions)), ['M2.5']);
  });

  it('acha pelo título e pelo código', () => {
    assert.deepEqual(codes(searchTechniques(nodes, { query: 'kimura' }, positions)), ['M7.1']);
    assert.deepEqual(codes(searchTechniques(nodes, { query: 'm3.7' }, positions)), ['M3.7']);
  });

  it('código vem antes de título, e título que começa vem antes de título que contém', () => {
    assert.deepEqual(codes(searchTechniques(nodes, { query: 'meia guarda' }, positions)), [
      'M3.6',
      'M3.7',
    ]);
  });

  it('acha pelo apelido da posição de onde a técnica parte', () => {
    // "lateral" não está em título nenhum: só no apelido da posição 100kg.
    assert.deepEqual(codes(searchTechniques(nodes, { query: 'lateral' }, positions)), [
      'M4.1',
      'M7.1',
    ]);
    assert.deepEqual(codes(searchTechniques(nodes, { query: 'half guard' }, positions)), [
      'M3.6',
      'M3.7',
    ]);
  });

  it('todas as palavras precisam aparecer em algum termo', () => {
    assert.deepEqual(codes(searchTechniques(nodes, { query: 'kimura costas' }, positions)), [
      'M7.1',
    ]);
    assert.deepEqual(searchTechniques(nodes, { query: 'kimura montada' }, positions), []);
  });

  it('o filtro por posição lista só as técnicas daquela posição', () => {
    assert.deepEqual(codes(searchTechniques(nodes, { position: 'MEIA_GUARDA' }, positions)), [
      'M3.6',
      'M3.7',
    ]);
    assert.deepEqual(
      codes(searchTechniques(nodes, { query: 'kimura', position: 'GUARDA_FECHADA' }, positions)),
      ['M7.1'],
    );
    assert.deepEqual(
      searchTechniques(nodes, { query: 'kimura', position: 'MEIA_GUARDA' }, positions),
      [],
    );
  });

  it('nó sem posição nem apelido não quebra a busca', () => {
    assert.deepEqual(codes(searchTechniques(nodes, { query: 'etiqueta' }, positions)), ['M0.1']);
  });
});

describe('nodesFromPosition', () => {
  it('devolve os nós da posição na ordem da árvore', () => {
    assert.deepEqual(codes(nodesFromPosition(nodes, 'CEM_QUILOS')), ['M4.1', 'M7.1']);
    assert.deepEqual(nodesFromPosition(nodes, 'EM_PE'), []);
  });
});

describe('groupPositionsByFamily', () => {
  it('agrupa na ordem das famílias e omite família vazia', () => {
    const grupos = groupPositionsByFamily(positions);
    assert.deepEqual(
      grupos.map((g) => g.family),
      ['GUARDA', 'CONTROLE_POR_CIMA', 'EM_PE'],
    );
    assert.deepEqual(
      grupos[0]?.positions.map((p) => p.code),
      ['MEIA_GUARDA', 'GUARDA_FECHADA'],
    );
    assert.equal(grupos[0]?.label, 'Guardas');
  });
});
