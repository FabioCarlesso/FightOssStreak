import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import {
  codigoDeSaida,
  coletarVideos,
  formatarRelatorio,
  lerModulos,
} from './verificar-videos.mjs';

const modulo = (arquivo, nodes) => ({ arquivo, dados: { nodes } });
const comVideo = (code, youtubeId) => ({ code, title: `nó ${code}`, video: { youtubeId } });
const semVideo = (code) => ({ code, title: `nó ${code}`, video: null });

describe('coleta do que há para verificar', () => {
  it('pega os nós com vídeo e ignora os que estão com video: null', () => {
    const videos = coletarVideos([
      modulo('m0.json', [comVideo('M0.1', 'REFdmhRCsSQ'), semVideo('M0.2')]),
      modulo('m2.json', [semVideo('M2.1'), comVideo('M2.2', 'aaaaaaaaaaa')]),
    ]);

    assert.deepEqual(
      videos.map(({ code, youtubeId, arquivo }) => ({ code, youtubeId, arquivo })),
      [
        { code: 'M0.1', youtubeId: 'REFdmhRCsSQ', arquivo: 'm0.json' },
        { code: 'M2.2', youtubeId: 'aaaaaaaaaaa', arquivo: 'm2.json' },
      ],
    );
  });

  it('currículo inteiro sem vídeo é "nada a verificar", não erro', () => {
    const videos = coletarVideos([modulo('m4.json', [semVideo('M4.1'), semVideo('M4.2')])]);

    assert.deepEqual(videos, []);
    assert.equal(formatarRelatorio([]), 'Nenhum vídeo catalogado — nada a verificar.');
    assert.equal(codigoDeSaida([]), 0);
  });

  it('lê o currículo real e encontra os vídeos já catalogados', () => {
    const modulos = lerModulos();

    assert.deepEqual(
      modulos.map(({ arquivo }) => arquivo),
      ['m0.json', 'm1.json', 'm2.json', 'm3.json', 'm4.json', 'm5.json', 'm6.json', 'm7.json', 'm8.json'],
    );
    for (const { code, youtubeId } of coletarVideos(modulos)) {
      assert.match(code, /^M\d+\.\d+$/);
      assert.match(youtubeId, /^[\w-]{11}$/);
    }
  });
});

describe('relatório e código de saída', () => {
  const ok = { code: 'M0.1', youtubeId: 'REFdmhRCsSQ', status: 'ok' };
  const removido = {
    code: 'M1.2',
    youtubeId: 'bbbbbbbbbbb',
    status: 'indisponivel',
    motivo: 'HTTP 404',
  };
  const bloqueado = {
    code: 'M1.6',
    youtubeId: 'ccccccccccc',
    status: 'nao-incorporavel',
    motivo: 'o autor desativou a incorporação',
  };
  const semResposta = {
    code: 'M0.3',
    youtubeId: 'ddddddddddd',
    status: 'indeterminado',
    motivo: 'ENOTFOUND',
  };

  it('sem problema, a mensagem diz claramente que está tudo disponível', () => {
    const relatorio = formatarRelatorio([ok, { ...ok, code: 'M0.2' }]);

    assert.match(relatorio, /Tudo disponível: 2 de 2/);
    assert.equal(codigoDeSaida([ok]), 0);
  });

  it('vídeo quebrado aparece com código do nó, id e motivo', () => {
    const relatorio = formatarRelatorio([ok, removido, bloqueado]);

    assert.match(relatorio, /2 de 3 vídeos catalogados com problema/);
    assert.match(relatorio, /M1\.2 +bbbbbbbbbbb {2}INDISPONÍVEL — HTTP 404/);
    assert.match(relatorio, /M1\.6 +ccccccccccc {2}NÃO INCORPORÁVEL — o autor desativou/);
    assert.doesNotMatch(relatorio, /M0\.1/);
    assert.match(relatorio, /catalogar-video\.mjs/);
    assert.equal(codigoDeSaida([ok, removido, bloqueado]), 1);
  });

  it('não ter conseguido verificar é reportado à parte, com código de saída próprio', () => {
    const relatorio = formatarRelatorio([ok, semResposta]);

    assert.match(relatorio, /Tudo disponível: 1 de 2/);
    assert.match(relatorio, /1 não deu para verificar/);
    assert.match(relatorio, /M0\.3 +ddddddddddd {2}não verificado — ENOTFOUND/);
    assert.equal(codigoDeSaida([ok, semResposta]), 2);
  });

  it('vídeo quebrado prevalece sobre não-verificado no código de saída', () => {
    assert.equal(codigoDeSaida([removido, semResposta]), 1);
  });

  it('sem nenhuma resposta do YouTube, não afirma que está tudo disponível', () => {
    const relatorio = formatarRelatorio([semResposta, { ...semResposta, code: 'M0.4' }]);

    assert.doesNotMatch(relatorio, /Tudo disponível/);
    assert.match(relatorio, /Nenhum vídeo pôde ser verificado/);
    assert.equal(codigoDeSaida([semResposta]), 2);
  });
});
