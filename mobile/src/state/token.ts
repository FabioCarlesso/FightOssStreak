import * as SecureStore from 'expo-secure-store';

/**
 * O token do app (D68), guardado no `expo-secure-store` — Keychain no iOS, Keystore no Android.
 *
 * O valor fica também em memória: o cliente da API pede o token a cada requisição, e ir ao
 * armazenamento seguro toda vez custaria uma ida ao nativo por chamada.
 */
const CHAVE = 'fos.token';

let emMemoria: string | null | undefined;

export const tokenStore = {
  async get(): Promise<string | null> {
    if (emMemoria === undefined) {
      emMemoria = await SecureStore.getItemAsync(CHAVE);
    }
    return emMemoria;
  },
  async set(token: string): Promise<void> {
    emMemoria = token;
    await SecureStore.setItemAsync(CHAVE, token);
  },
  async clear(): Promise<void> {
    emMemoria = null;
    await SecureStore.deleteItemAsync(CHAVE);
  },
};

export type TokenStore = typeof tokenStore;
