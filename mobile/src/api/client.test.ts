import { acessoRecusado, semSessao } from '../state/avisos';
import { createAppApi } from './client';

function resposta(status: number, corpo: unknown = {}) {
  return Promise.resolve(
    new Response(JSON.stringify(corpo), {
      status,
      headers: { 'Content-Type': 'application/json' },
    }),
  );
}

describe('cliente da API do app', () => {
  it('401 numa chamada com token avisa que a sessão acabou', async () => {
    const ouvinte = jest.fn();
    const desfazer = semSessao.assinar(ouvinte);
    const api = createAppApi({
      baseUrl: 'http://api.test',
      tokens: { get: () => Promise.resolve('vencido') },
      fetch: () => resposta(401, { error: 'token_invalido', message: 'Entre de novo.' }),
    });

    await expect(api.getStreak()).rejects.toMatchObject({ status: 401 });
    expect(ouvinte).toHaveBeenCalledTimes(1);
    desfazer();
  });

  it('401 do login (sem token) é resposta da tela, não sessão perdida', async () => {
    const ouvinte = jest.fn();
    const desfazer = semSessao.assinar(ouvinte);
    const api = createAppApi({
      baseUrl: 'http://api.test',
      tokens: { get: () => Promise.resolve('qualquer') },
      fetch: () => resposta(401, { error: 'credencial_invalida', message: 'Não confere.' }),
    });

    await expect(api.mobileLoginWithPassword('a@b.c', 'x')).rejects.toMatchObject({ status: 401 });
    expect(ouvinte).not.toHaveBeenCalled();
    desfazer();
  });

  it('403 acesso_recusado avisa o portão; em /api/me, não', async () => {
    const ouvinte = jest.fn();
    const desfazer = acessoRecusado.assinar(ouvinte);
    const api = createAppApi({
      baseUrl: 'http://api.test',
      tokens: { get: () => Promise.resolve('token') },
      fetch: () => resposta(403, { error: 'acesso_recusado', message: 'Bloqueada.' }),
    });

    await expect(api.getStreak()).rejects.toMatchObject({ status: 403 });
    await expect(api.getAccount()).rejects.toMatchObject({ status: 403 });
    expect(ouvinte).toHaveBeenCalledTimes(1);
    desfazer();
  });
});
