import * as SecureStore from 'expo-secure-store';

import { PREFERENCIAS_PADRAO, type Preferencias } from './plano';

/**
 * Ligado e horário do lembrete, guardados **só no aparelho** (#142): não são dado da conta, e o
 * backend não precisa saber a que horas a pessoa quer ser lembrada.
 *
 * Fica no `expo-secure-store`, que o app já usa para o token, para não trazer outra dependência de
 * armazenamento por dois números e um booleano.
 */
const CHAVE = 'fos.lembretes';

export interface PreferenciasStore {
  readonly get: () => Promise<Preferencias>;
  readonly set: (preferencias: Preferencias) => Promise<void>;
}

export const preferenciasStore: PreferenciasStore = {
  async get() {
    try {
      const salvo = await SecureStore.getItemAsync(CHAVE);
      return salvo ? validar(JSON.parse(salvo)) : PREFERENCIAS_PADRAO;
    } catch {
      return PREFERENCIAS_PADRAO;
    }
  },
  async set(preferencias) {
    await SecureStore.setItemAsync(CHAVE, JSON.stringify(preferencias));
  },
};

/** Valor salvo por uma versão anterior, ou corrompido, volta para o padrão em vez de quebrar. */
function validar(valor: unknown): Preferencias {
  const { ligado, hora, minuto } = (valor ?? {}) as Record<string, unknown>;
  if (typeof ligado !== 'boolean' || !inteiroEntre(hora, 0, 23) || !inteiroEntre(minuto, 0, 59)) {
    return PREFERENCIAS_PADRAO;
  }
  return { ligado, hora, minuto };
}

function inteiroEntre(valor: unknown, min: number, max: number): valor is number {
  return Number.isInteger(valor) && (valor as number) >= min && (valor as number) <= max;
}
