import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { describe, it } from 'node:test';

import {
  contextosDeJobs,
  contextosDisponiveis,
  contextosExigidos,
  formatarRelatorio,
  orfaos,
} from './verificar-ruleset.mjs';

const ruleset = (contextos) => ({
  rules: [
    { type: 'deletion' },
    {
      type: 'required_status_checks',
      parameters: { required_status_checks: contextos.map((context) => ({ context })) },
    },
  ],
});

describe('contextos exigidos pela ruleset', () => {
  it('lê os contextos da regra de required_status_checks', () => {
    assert.deepEqual(contextosExigidos(ruleset(['backend', 'web'])), ['backend', 'web']);
  });

  it('ruleset sem a regra de status check não exige contexto nenhum', () => {
    assert.deepEqual(contextosExigidos({ rules: [{ type: 'deletion' }] }), []);
  });

  it('lê a ruleset real do repositório e ela exige pelo menos um contexto', () => {
    // Ler o arquivo de verdade é o que faz o teste acusar se a regra de status check
    // desaparecer da ruleset — aí `main` passaria a aceitar merge sem CI.
    const real = JSON.parse(
      readFileSync(new URL('../.github/rulesets/main.json', import.meta.url)),
    );
    assert.ok(contextosExigidos(real).length > 0);
  });
});

describe('contextos que os workflows reportam', () => {
  it('usa o id do job quando ele não tem name:', () => {
    const yaml = ['name: web', 'on:', '  pull_request:', 'jobs:', '  web:', '    runs-on: x'].join(
      '\n',
    );
    assert.deepEqual(contextosDeJobs(yaml), ['web']);
  });

  it('usa o name: do job quando ele existe — é ele que vira o contexto do check', () => {
    const yaml = [
      'jobs:',
      '  web:',
      '    name: Web',
      '    runs-on: x',
      '    steps:',
      '      - name: Testes',
      '        run: npm test',
    ].join('\n');

    // "Testes" é name: de step e não pode virar contexto.
    assert.deepEqual(contextosDeJobs(yaml), ['Web']);
  });

  it('lê vários jobs e ignora comentários e linhas em branco', () => {
    const yaml = [
      'jobs:',
      '  # o nome do job é o contexto da ruleset',
      '  backend:',
      '    runs-on: x',
      '',
      '  web:',
      '    runs-on: x',
    ].join('\n');
    assert.deepEqual(contextosDeJobs(yaml), ['backend', 'web']);
  });

  it('para no fim do bloco jobs:', () => {
    const yaml = ['jobs:', '  web:', '    runs-on: x', 'permissions:', '  contents: read'].join(
      '\n',
    );
    assert.deepEqual(contextosDeJobs(yaml), ['web']);
  });

  it('workflow indentado com 4 espaços não vira "nenhum job"', () => {
    // Ler zero job faria a guarda acusar órfão onde não há — falso positivo pior que a falha
    // que ela existe para pegar.
    const yaml = ['jobs:', '    web:', '        runs-on: x'].join('\n');
    assert.deepEqual(contextosDeJobs(yaml), ['web']);
  });

  it('workflow sem bloco jobs: não reporta contexto', () => {
    assert.deepEqual(contextosDeJobs('name: nada\non:\n  push:\n'), []);
  });

  it('lê os workflows reais e encontra backend e web', () => {
    const disponiveis = contextosDisponiveis();
    assert.ok(disponiveis.has('backend'), 'job backend não encontrado nos workflows');
    assert.ok(disponiveis.has('web'), 'job web não encontrado nos workflows');
  });
});

describe('detecção de contexto órfão', () => {
  const disponiveis = new Map([
    ['backend', 'backend.yml'],
    ['web', 'web.yml'],
  ]);

  it('ruleset e workflows em dia não produzem órfão', () => {
    assert.deepEqual(orfaos(['backend', 'web'], disponiveis), []);
  });

  it('job renomeado sem atualizar a ruleset vira órfão', () => {
    const renomeado = new Map([
      ['backend', 'backend.yml'],
      ['frontend', 'web.yml'],
    ]);
    assert.deepEqual(orfaos(['backend', 'web'], renomeado), ['web']);
  });

  it('job que ganhou name: diferente do id vira órfão', () => {
    const comName = new Map([
      ['backend', 'backend.yml'],
      ['Web', 'web.yml'],
    ]);
    assert.deepEqual(orfaos(['backend', 'web'], comName), ['web']);
  });
});

describe('relatório', () => {
  const disponiveis = new Map([
    ['backend', 'backend.yml'],
    ['frontend', 'web.yml'],
  ]);

  it('diz qual contexto ficou órfão e o que rodar para corrigir', () => {
    const texto = formatarRelatorio(['backend', 'web'], disponiveis);

    assert.match(texto, /1 contexto\(s\) da ruleset sem job correspondente/);
    assert.match(texto, /^ {2}web$/m);
    assert.match(texto, /\.github\/rulesets\/main\.json/);
    assert.match(texto, /apply-repo-rules\.sh/);
    // Sem listar o que os workflows oferecem hoje, quem lê não sabe para que renomear.
    assert.match(texto, /frontend {2}\(web\.yml\)/);
  });

  it('estado em dia produz confirmação, não silêncio', () => {
    const texto = formatarRelatorio(['backend'], new Map([['backend', 'backend.yml']]));
    assert.match(texto, /batem/);
  });

  it('ruleset que não exige check nenhum é avisado, não tratado como sucesso', () => {
    const texto = formatarRelatorio([], disponiveis);
    assert.match(texto, /aceitando merge sem CI/);
  });
});
