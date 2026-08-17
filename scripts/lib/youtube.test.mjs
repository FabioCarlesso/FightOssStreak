import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { parseVideoId, verificarDisponibilidade } from './youtube.mjs';

/** Dublê de `fetch` que responde por URL, sem tocar na rede. */
function fetchFalso({ oembed, watch }) {
  return async (url) => {
    if (String(url).includes('/oembed')) return oembed();
    return watch();
  };
}

const oembedOk = () => ({
  ok: true,
  status: 200,
  json: async () => ({ title: '  Shrimping  ', author_name: ' Ritchie Yip ' }),
});
const oembedStatus = (status) => () => ({ ok: false, status, json: async () => ({}) });
const watchCom = (playableInEmbed) => () => ({
  ok: true,
  status: 200,
  text: async () => `{"videoDetails":{"playableInEmbed":${playableInEmbed}}}`,
});

describe('parseVideoId', () => {
  it('aceita as formas de URL do YouTube e o id cru', () => {
    for (const entrada of [
      'https://www.youtube.com/watch?v=REFdmhRCsSQ',
      'https://youtu.be/REFdmhRCsSQ',
      'https://www.youtube.com/shorts/REFdmhRCsSQ',
      'https://www.youtube.com/embed/REFdmhRCsSQ',
      'REFdmhRCsSQ',
    ]) {
      assert.equal(parseVideoId(entrada), 'REFdmhRCsSQ');
    }
  });

  it('recusa o que não contém um id de 11 caracteres', () => {
    assert.throws(() => parseVideoId('https://vimeo.com/12345'), /id de vídeo/);
    assert.throws(() => parseVideoId('não é url'), /URL inválida/);
  });
});

describe('verificarDisponibilidade', () => {
  it('vídeo público e incorporável é ok, com título e canal aparados', async () => {
    const resultado = await verificarDisponibilidade('REFdmhRCsSQ', {
      fetchImpl: fetchFalso({ oembed: oembedOk, watch: watchCom(true) }),
    });

    assert.equal(resultado.status, 'ok');
    assert.equal(resultado.title, 'Shrimping');
    assert.equal(resultado.channel, 'Ritchie Yip');
    assert.equal(resultado.incorporacaoConfirmada, true);
  });

  it('vídeo removido ou privado é indisponível, com o motivo', async () => {
    for (const status of [404, 401]) {
      const resultado = await verificarDisponibilidade('REFdmhRCsSQ', {
        fetchImpl: fetchFalso({ oembed: oembedStatus(status), watch: watchCom(true) }),
      });

      assert.equal(resultado.status, 'indisponivel');
      assert.match(resultado.motivo, new RegExp(String(status)));
    }
  });

  it('incorporação desativada pelo autor é problema, não ok (D7)', async () => {
    const resultado = await verificarDisponibilidade('REFdmhRCsSQ', {
      fetchImpl: fetchFalso({ oembed: oembedOk, watch: watchCom(false) }),
    });

    assert.equal(resultado.status, 'nao-incorporavel');
    assert.match(resultado.motivo, /D7/);
  });

  it('YouTube instável não vira acusação contra o vídeo', async () => {
    const resultado = await verificarDisponibilidade('REFdmhRCsSQ', {
      fetchImpl: fetchFalso({ oembed: oembedStatus(500), watch: watchCom(true) }),
    });

    assert.equal(resultado.status, 'indeterminado');
  });

  it('rede caindo também é indeterminado, não indisponível', async () => {
    const resultado = await verificarDisponibilidade('REFdmhRCsSQ', {
      fetchImpl: async () => {
        throw new Error('getaddrinfo ENOTFOUND www.youtube.com');
      },
    });

    assert.equal(resultado.status, 'indeterminado');
    assert.match(resultado.motivo, /ENOTFOUND/);
  });

  it('página do watch inacessível não impede concluir que o vídeo existe', async () => {
    const resultado = await verificarDisponibilidade('REFdmhRCsSQ', {
      fetchImpl: fetchFalso({
        oembed: oembedOk,
        watch: () => {
          throw new Error('conexão fechada');
        },
      }),
    });

    assert.equal(resultado.status, 'ok');
    assert.equal(resultado.incorporacaoConfirmada, false);
  });
});
