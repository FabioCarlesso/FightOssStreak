import { createApiClient } from '@fos/api-client';
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * O que o cliente acrescenta em cada requisição.
 *
 * Vale um teste porque as duas coisas são invisíveis até quebrarem: sem o cookie de sessão a API
 * responde 401 em tudo, e sem o token de CSRF toda escrita volta 403 — o app inteiro pararia, e o
 * sintoma não apontaria para cá.
 */
function respostaVazia(): Response {
  return new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } });
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  fetchMock = vi.fn().mockResolvedValue(respostaVazia());
});

function client() {
  return createApiClient({ fetch: fetchMock as unknown as typeof globalThis.fetch });
}

function ultimaRequisicao(): RequestInit {
  return fetchMock.mock.calls.at(-1)?.[1] as RequestInit;
}

function cabecalhos(): Record<string, string> {
  return (ultimaRequisicao().headers ?? {}) as Record<string, string>;
}

describe('cliente da API', () => {
  it('manda o cookie de sessão em toda requisição', async () => {
    await client().getStreak();

    expect(ultimaRequisicao().credentials).toBe('same-origin');
  });

  it('escrita leva o token de CSRF que o backend deixou no cookie', async () => {
    document.cookie = 'XSRF-TOKEN=token-do-backend';

    await client().acceptDisclaimer('2026-08');

    expect(cabecalhos()['X-XSRF-TOKEN']).toBe('token-do-backend');
  });

  it('leitura não leva token: GET não é o que o CSRF protege', async () => {
    document.cookie = 'XSRF-TOKEN=token-do-backend';

    await client().getStreak();

    expect(cabecalhos()['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('sem cookie, a escrita sai sem o header em vez de mandar "undefined"', async () => {
    await client().acceptDisclaimer('2026-08');

    expect(cabecalhos()['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('401 vira ApiError reconhecível, que é o que leva à tela de login', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ error: 'nao_autenticado', message: 'sem sessão' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await expect(client().getAccount()).rejects.toMatchObject({
      status: 401,
      code: 'nao_autenticado',
    });
  });

  it('403 de acesso pendente é distinguível do 403 de CSRF', async () => {
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ error: 'acesso_pendente', message: 'na fila' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await expect(client().getStreak()).rejects.toMatchObject({ code: 'acesso_pendente' });
  });

  it('204 sem corpo não estoura ao ser lido', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    await expect(client().logout()).resolves.toBeUndefined();
  });
});
