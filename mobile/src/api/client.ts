import { createApiClient } from '@fos/api-client';

/**
 * Onde o app encontra a API. `EXPO_PUBLIC_*` é embutido no bundle pelo Metro na subida, então
 * mudar o valor exige reiniciar o `expo start`. Sem ele a tela de fumaça diz o que configurar, em
 * vez de tentar um endereço que não existe no aparelho (ver mobile/.env.example).
 */
export const apiUrl: string | undefined = textoOuNada(process.env.EXPO_PUBLIC_API_URL);

export const api = createApiClient({ baseUrl: apiUrl });

export type Api = Pick<typeof api, 'getAuthProviders'>;

/**
 * O `process.env` do React Native não é tipado. O acesso acima precisa continuar literal — é
 * esse texto que o Metro troca pelo valor —, e a conferência do tipo fica aqui.
 */
function textoOuNada(valor: unknown): string | undefined {
  return typeof valor === 'string' && valor !== '' ? valor : undefined;
}
