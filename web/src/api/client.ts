import { createApiClient } from '@fos/api-client';

/**
 * Instância única do cliente. Caminhos relativos: em dev o Vite faz proxy de `/api` para o
 * backend, e em produção os dois são servidos pela mesma origem.
 */
export const api = createApiClient();

export { ApiError } from '@fos/api-client';
