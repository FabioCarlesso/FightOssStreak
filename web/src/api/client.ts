import { createApiClient } from '@fos/api-client';
import { notifyAccessDenied } from '../state/accessDenied.ts';

/**
 * Instância única do cliente. Caminhos relativos: em dev o Vite faz proxy de `/api` para o
 * backend, e em produção os dois são servidos pela mesma origem.
 */
export const api = createApiClient({ fetch: observandoAcessoRecusado });

export { ApiError } from '@fos/api-client';

/**
 * `fetch` do app, com um único acréscimo: perceber que esta conta foi bloqueada (#90).
 *
 * O bloqueio chega no meio do uso — o servidor derruba a sessão e a chamada seguinte volta `403`
 * com `acesso_recusado`. Quem sabe disso é uma tela qualquer, que só saberia mostrar a mensagem de
 * erro dela; quem sabe **o que fazer** com essa informação é o `AuthGate`. O aviso liga os dois sem
 * que cada tela precise conhecer o caso.
 *
 * `/api/me` fica de fora: ele está fora do portão do backend e responde 200 com o estado no corpo.
 * Se um dia responder 403, avisar aqui faria o portão reconsultá-lo em laço.
 */
async function observandoAcessoRecusado(
  input: RequestInfo | URL,
  init?: RequestInit,
): Promise<Response> {
  const response = await fetch(input, init);
  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
  if (response.status === 403 && !url.endsWith('/api/me')) {
    try {
      // Clone: o corpo é de uso único, e quem chamou ainda precisa lê-lo para montar o `ApiError`.
      const body = (await response.clone().json()) as { error?: string };
      if (body.error === 'acesso_recusado') notifyAccessDenied();
    } catch {
      // 403 sem corpo JSON — token de CSRF, por exemplo. Não é este caso.
    }
  }
  return response;
}
