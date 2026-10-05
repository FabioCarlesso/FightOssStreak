import { createApiClient } from '@fos/api-client';

import { acessoRecusado, semSessao } from '../state/avisos';
import { tokenStore, type TokenStore } from '../state/token';

/**
 * Onde o app encontra a API. `EXPO_PUBLIC_*` é embutido no bundle pelo Metro na subida, então
 * mudar o valor exige reiniciar o `expo start` (ver mobile/.env.example).
 */
export const apiUrl: string | undefined = textoOuNada(process.env.EXPO_PUBLIC_API_URL);

/** O site, para o que continua só na web: cadastro e recuperação de senha (D68). */
export const webUrl: string | undefined = textoOuNada(process.env.EXPO_PUBLIC_WEB_URL);

export function createAppApi({
  baseUrl = apiUrl,
  tokens = tokenStore,
  fetch: baseFetch = globalThis.fetch.bind(globalThis),
}: {
  baseUrl?: string;
  tokens?: Pick<TokenStore, 'get'>;
  fetch?: typeof globalThis.fetch;
} = {}) {
  return createApiClient({
    baseUrl,
    accessToken: () => tokens.get(),
    fetch: observando(baseFetch),
  });
}

export type Api = ReturnType<typeof createAppApi>;

export { ApiError } from '@fos/api-client';

/**
 * O `fetch` do app, com dois acréscimos que nenhuma tela precisa conhecer.
 *
 * - **401 em requisição com token:** o token morreu, e o app volta ao login. Só com token, porque o
 *   401 do login por senha errada é resposta da tela, não sessão perdida.
 * - **403 `acesso_recusado`:** a conta foi bloqueada (#90). `/api/me` fica de fora, como no web: ele
 *   responde 200 com o estado no corpo, e avisar daqui faria o portão reconsultá-lo em laço.
 */
function observando(baseFetch: typeof globalThis.fetch): typeof globalThis.fetch {
  return async (input, init) => {
    const response = await baseFetch(input, init);
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const headers = (init?.headers ?? {}) as Record<string, string>;
    if (response.status === 401 && headers['Authorization']) {
      semSessao.avisar();
    }
    if (response.status === 403 && !url.endsWith('/api/me')) {
      try {
        // Clone: o corpo é de uso único, e quem chamou ainda precisa lê-lo para montar o erro.
        const body = (await response.clone().json()) as { error?: string };
        if (body.error === 'acesso_recusado') acessoRecusado.avisar();
      } catch {
        // 403 sem corpo JSON: não é este caso.
      }
    }
    return response;
  };
}

/**
 * O `process.env` do React Native não é tipado. O acesso acima precisa continuar literal — é
 * esse texto que o Metro troca pelo valor —, e a conferência do tipo fica aqui.
 */
function textoOuNada(valor: unknown): string | undefined {
  return typeof valor === 'string' && valor !== '' ? valor : undefined;
}
