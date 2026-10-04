import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { createApiClient } from './index.ts';

/** O que o cliente pediu ao `fetch`, para conferir cabeçalho e credencial sem rede. */
interface Chamada {
  readonly url: string;
  readonly init: RequestInit;
}

function fetchFalso(chamadas: Chamada[], corpo: unknown = {}) {
  return ((url: string, init: RequestInit) => {
    chamadas.push({ url, init });
    return Promise.resolve(
      new Response(JSON.stringify(corpo), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
  }) as unknown as typeof globalThis.fetch;
}

function cabecalhos(chamada: Chamada): Record<string, string> {
  return chamada.init.headers as Record<string, string>;
}

describe('transporte do api-client', () => {
  it('com token, manda Authorization Bearer e nenhum cookie (D68)', async () => {
    const chamadas: Chamada[] = [];
    const api = createApiClient({
      fetch: fetchFalso(chamadas),
      // Assíncrono de propósito: é assim que o `expo-secure-store` devolve o token.
      accessToken: () => Promise.resolve('token-do-aparelho'),
    });

    await api.getStreak();

    assert.equal(cabecalhos(chamadas[0])['Authorization'], 'Bearer token-do-aparelho');
    assert.equal(chamadas[0].init.credentials, 'omit');
  });

  it('com token, escrita não procura CSRF: o backend dispensa quem manda o cabeçalho', async () => {
    const chamadas: Chamada[] = [];
    const api = createApiClient({ fetch: fetchFalso(chamadas), accessToken: () => 't' });

    await api.mobileLogout().catch(() => undefined);

    assert.equal(cabecalhos(chamadas[0])['X-XSRF-TOKEN'], undefined);
    assert.equal(chamadas[0].init.method, 'POST');
  });

  it('sem token — a web —, segue na sessão por cookie e sem Authorization', async () => {
    const chamadas: Chamada[] = [];
    const api = createApiClient({ fetch: fetchFalso(chamadas) });

    await api.getStreak();

    assert.equal(cabecalhos(chamadas[0])['Authorization'], undefined);
    assert.equal(chamadas[0].init.credentials, 'same-origin');
  });

  it('token nulo (ainda sem login) cai no caminho sem cabeçalho', async () => {
    const chamadas: Chamada[] = [];
    const api = createApiClient({
      fetch: fetchFalso(chamadas, { token: 'novo' }),
      baseUrl: 'https://fos.example.test/',
      accessToken: () => null,
    });

    const resposta = await api.mobileLoginWithPassword('aluno@example.test', 'senha-longa-12');

    assert.equal(resposta.token, 'novo');
    assert.equal(chamadas[0].url, 'https://fos.example.test/api/mobile/auth/senha');
    assert.equal(cabecalhos(chamadas[0])['Authorization'], undefined);
    assert.deepEqual(JSON.parse(chamadas[0].init.body as string), {
      email: 'aluno@example.test',
      senha: 'senha-longa-12',
    });
  });

  it('token velho no aparelho não vai nas rotas de entrar nem na versão mínima', async () => {
    const chamadas: Chamada[] = [];
    const api = createApiClient({ fetch: fetchFalso(chamadas), accessToken: () => 'vencido' });

    await api.mobileLoginWithPassword('aluno@example.test', 'senha-longa-12');
    await api.mobileLoginWithGoogle('id-token');
    await api.mobileLoginWithApple({ identityToken: 'x', nonce: 'n' });
    await api.getAppVersion();
    await api.getAuthProviders();

    for (const chamada of chamadas) {
      assert.equal(cabecalhos(chamada)['Authorization'], undefined, chamada.url);
    }
  });

  it('sair leva o token: é ele que o backend revoga', async () => {
    const chamadas: Chamada[] = [];
    const api = createApiClient({ fetch: fetchFalso(chamadas), accessToken: () => 'este' });

    await api.mobileLogout().catch(() => undefined);

    assert.equal(cabecalhos(chamadas[0])['Authorization'], 'Bearer este');
  });
});
