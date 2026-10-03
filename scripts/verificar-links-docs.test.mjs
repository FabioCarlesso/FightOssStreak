import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
  ancoraDoTitulo,
  ancorasDoMarkdown,
  formatarRelatorio,
  linksDoMarkdown,
  mencoesADocs,
  problemaDoAlvo,
  verificar,
  verificavel,
} from './verificar-links-docs.mjs';

describe('âncora gerada para um título', () => {
  it('segue o GitHub: minúsculas, sem pontuação, espaço vira hífen, acento fica', () => {
    assert.equal(ancoraDoTitulo('Onde documentar cada mudança'), 'onde-documentar-cada-mudança');
    assert.equal(
      ancoraDoTitulo('Política de uso de vídeo (D7) — limites'),
      'política-de-uso-de-vídeo-d7--limites',
    );
  });

  it('crase e link somem, o texto do link fica', () => {
    assert.equal(ancoraDoTitulo('Prévia de link (`og.jpg`)'), 'prévia-de-link-ogjpg');
    assert.equal(ancoraDoTitulo('Ver [o log](07-decisoes.md)'), 'ver-o-log');
  });
});

describe('âncoras de um Markdown', () => {
  it('título repetido ganha sufixo, e título dentro de bloco de código não conta', () => {
    const md = ['# Log', '## Uso', '```', '## Não é título', '```', '## Uso'].join('\n');
    assert.deepEqual([...ancorasDoMarkdown(md)], ['log', 'uso', 'uso-1']);
  });

  it('id de HTML embutido é âncora', () => {
    assert.ok(ancorasDoMarkdown('<a id="d55"></a>').has('d55'));
  });
});

describe('links de um Markdown', () => {
  it('lê link inline e de referência e ignora URL externa', () => {
    const md = [
      'Veja [o índice](docs/README.md#indice) e [o site](https://x.dev).',
      '[ref]: ../CLAUDE.md',
    ].join('\n');
    assert.deepEqual(
      linksDoMarkdown(md).map((l) => l.alvo),
      ['docs/README.md#indice', '../CLAUDE.md'],
    );
  });

  it('ignora link dentro de crase e de bloco de código', () => {
    const md = ['`[x](nada.md)`', '```', '[y](nada.md)', '```'].join('\n');
    assert.deepEqual(linksDoMarkdown(md), []);
  });

  it('guarda o número da linha', () => {
    assert.equal(linksDoMarkdown('a\n\n[x](y.md)')[0].numero, 3);
  });
});

describe('menções a docs/', () => {
  it('acha caminho citado em comentário de código, com âncora', () => {
    const java = '// ver docs/11-privacidade.md#retenção e docs/07-decisoes.md';
    assert.deepEqual(
      mencoesADocs(java).map((m) => m.alvo),
      ['docs/11-privacidade.md#retenção', 'docs/07-decisoes.md'],
    );
  });

  it('acha a URL deste repositório no GitHub, que é o link público da landing', () => {
    const ts = 'export const URL = `${REPO_URL}/blob/main/docs/produto/disclaimer.md`;';
    const md = 'https://github.com/FabioCarlesso/FightOssStreak/blob/main/docs/api.md#erros';
    assert.deepEqual(
      mencoesADocs(ts).map((m) => m.alvo),
      ['docs/produto/disclaimer.md'],
    );
    assert.deepEqual(
      mencoesADocs(md).map((m) => m.alvo),
      ['docs/api.md#erros'],
    );
  });

  it('não confunde com caminho de outro repositório ou de subpasta', () => {
    const texto = 'https://github.com/outro/repo/blob/main/docs/api.md e web/docs/x.md';
    assert.deepEqual(mencoesADocs(texto), []);
  });
});

describe('resolução do alvo', () => {
  const contexto = {
    existe: (caminho) => ['/r/docs/a.md', '/r/docs', '/r/README.md'].includes(caminho),
    ancorasDe: () => new Set(['secao', 'mudança']),
    arquivoAtual: '/r/README.md',
  };

  it('arquivo e âncora que existem resolvem', () => {
    assert.equal(problemaDoAlvo('docs/a.md#secao', '/r', contexto), null);
    assert.equal(problemaDoAlvo('docs/', '/r', contexto), null);
  });

  it('âncora codificada na URL é decodificada antes de comparar', () => {
    assert.equal(problemaDoAlvo('docs/a.md#mudan%C3%A7a', '/r', contexto), null);
  });

  it('arquivo que não existe é apontado', () => {
    assert.match(problemaDoAlvo('docs/b.md', '/r', contexto), /arquivo inexistente/);
  });

  it('âncora que não existe é apontada, inclusive a do próprio arquivo', () => {
    assert.match(problemaDoAlvo('docs/a.md#outra', '/r', contexto), /âncora inexistente/);
    assert.match(problemaDoAlvo('#outra', '/r', contexto), /âncora inexistente/);
  });
});

describe('arquivos verificados', () => {
  it('migration fica de fora: corrigir comentário nela muda o checksum do Flyway', () => {
    assert.equal(verificavel('backend/src/main/resources/db/migration/V1__schema.sql'), false);
    assert.equal(verificavel('backend/src/main/java/dev/fos/service/ClientIp.java'), true);
  });

  it('gerado fica de fora; Markdown e código entram', () => {
    assert.equal(verificavel('shared/types/generated/api.ts'), false);
    assert.equal(verificavel('docs/README.md'), true);
    assert.equal(verificavel('backend/Dockerfile'), true);
  });
});

describe('repositório real', () => {
  it('não tem link nem menção a documentação quebrada', () => {
    // É o mesmo que o passo do CI faz; aqui a quebra aparece já no `npm test` local.
    const quebrados = verificar();
    assert.deepEqual(quebrados, [], formatarRelatorio(quebrados));
  });
});
