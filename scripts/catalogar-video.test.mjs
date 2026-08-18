import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { appendExtraVideos, replaceVideoBlock } from './catalogar-video.mjs';

/**
 * A edição do currículo é cirúrgica de propósito: re-serializar o JSON inteiro reformataria as
 * alternativas do quiz — escritas em uma linha cada — e transformaria "acrescentei um clipe" num
 * diff de centenas de linhas. O que estes testes cobrem é justamente o que a cirurgia pode errar:
 * acertar o nó errado, corromper o arquivo, ou perder o que já estava lá.
 */
const arquivo = (...nodes) =>
  `{
  "code": "M1",
  "nodes": [
${nodes.join(',\n')}
  ]
}
`;

const no = (code, extras = null) => `    {
      "code": ${JSON.stringify(code)},
      "title": "titulo de ${code}",
      "video": {
        "youtubeId": "canonic${code.at(-1)}aa",
        "title": "canônico",
        "channel": "Canal"
      }${extras === null ? '' : `,\n      "extraVideos": [${extras}]`},
      "quiz": [
        {
          "prompt": "p",
          "options": [{ "text": "a", "correct": true }, { "text": "b", "correct": false }]
        }
      ]
    }`;

const clipe = (youtubeId, resto = {}) => ({
  youtubeId,
  title: `título de ${youtubeId}`,
  channel: 'Guiabasicodejiujitsu',
  ...resto,
});

describe('appendExtraVideos', () => {
  it('cria o campo extraVideos no nó que ainda não tem, logo depois do canônico', () => {
    const texto = arquivo(no('M1.1'), no('M1.2'));

    const dados = JSON.parse(appendExtraVideos(texto, 'M1.1', [clipe('AkQxiCrsGo4')]));

    assert.deepEqual(
      dados.nodes[0].extraVideos.map((extra) => extra.youtubeId),
      ['AkQxiCrsGo4'],
    );
    assert.equal(dados.nodes[1].extraVideos, undefined);
  });

  it('acrescenta ao que já existe, preservando os clipes anteriores e a ordem', () => {
    const texto = arquivo(
      no(
        'M1.1',
        `
        {
          "youtubeId": "TmzSPs_92bk",
          "title": "primeiro",
          "channel": "Canal"
        }
      `,
      ),
    );

    const dados = JSON.parse(
      appendExtraVideos(texto, 'M1.1', [clipe('qHK0HembKi8'), clipe('9wEhZ1PdoMI')]),
    );

    assert.deepEqual(
      dados.nodes[0].extraVideos.map((extra) => extra.youtubeId),
      ['TmzSPs_92bk', 'qHK0HembKi8', '9wEhZ1PdoMI'],
    );
  });

  /**
   * O caso que motivou `endOfNode`. `extraVideos` é opcional, então procurá-lo sem limite superior
   * encontraria o campo de um nó posterior e gravaria o clipe no nó errado — em silêncio, porque o
   * arquivo continuaria sendo JSON válido.
   */
  it('não invade o extraVideos de um nó seguinte quando o nó alvo ainda não tem o campo', () => {
    const texto = arquivo(
      no('M1.1'),
      no(
        'M1.2',
        `
        {
          "youtubeId": "TmzSPs_92bk",
          "title": "do M1.2",
          "channel": "Canal"
        }
      `,
      ),
    );

    const dados = JSON.parse(appendExtraVideos(texto, 'M1.1', [clipe('AkQxiCrsGo4')]));

    assert.deepEqual(
      dados.nodes[0].extraVideos.map((extra) => extra.youtubeId),
      ['AkQxiCrsGo4'],
    );
    assert.deepEqual(
      dados.nodes[1].extraVideos.map((extra) => extra.youtubeId),
      ['TmzSPs_92bk'],
    );
  });

  it('grava orientação e anotação quando vêm no clipe', () => {
    const texto = arquivo(no('M1.1'));

    const dados = JSON.parse(
      appendExtraVideos(texto, 'M1.1', [
        clipe('AkQxiCrsGo4', { orientation: 'VERTICAL', note: 'o giro para o lado da cabeça' }),
      ]),
    );

    assert.deepEqual(dados.nodes[0].extraVideos[0], {
      youtubeId: 'AkQxiCrsGo4',
      title: 'título de AkQxiCrsGo4',
      channel: 'Guiabasicodejiujitsu',
      orientation: 'VERTICAL',
      note: 'o giro para o lado da cabeça',
    });
  });

  it('não toca no quiz nem no canônico do nó alterado', () => {
    const texto = arquivo(no('M1.1'));

    const depois = appendExtraVideos(texto, 'M1.1', [clipe('AkQxiCrsGo4')]);

    assert.ok(depois.includes('{ "text": "a", "correct": true }'));
    assert.equal(JSON.parse(depois).nodes[0].video.youtubeId, 'canonic1aa');
  });

  it('recusa nó inexistente em vez de gravar em lugar nenhum', () => {
    assert.throws(
      () => appendExtraVideos(arquivo(no('M1.1')), 'M9.9', [clipe('AkQxiCrsGo4')]),
      /Não achei/,
    );
  });
});

describe('replaceVideoBlock continua trocando só o canônico', () => {
  it('substitui o vídeo sem levar junto os complementares do nó', () => {
    const texto = arquivo(
      no(
        'M1.1',
        `
        {
          "youtubeId": "TmzSPs_92bk",
          "title": "clipe",
          "channel": "Canal"
        }
      `,
      ),
    );

    const dados = JSON.parse(
      replaceVideoBlock(texto, 'M1.1', {
        youtubeId: 'REFdmhRCsSQ',
        title: 'novo canônico',
        channel: 'Outro Canal',
      }),
    );

    assert.equal(dados.nodes[0].video.youtubeId, 'REFdmhRCsSQ');
    assert.deepEqual(
      dados.nodes[0].extraVideos.map((extra) => extra.youtubeId),
      ['TmzSPs_92bk'],
    );
  });
});
