import type { AccountView } from '@fos/types';
import { render } from '@testing-library/react-native';
import type { ReactElement } from 'react';

import { ApiError, type Api } from '../api/client';
import { ApiProvider } from '../state/api';
import { SessionProvider } from '../state/session';
import type { TokenStore } from '../state/token';

/**
 * Peças dos testes de fluxo: uma API falsa que responde o mínimo para o app abrir, um cofre de token
 * em memória e o render com os provedores. Cada teste sobrescreve só o que exercita.
 */

export const CONTA: AccountView = {
  displayName: 'Aluno Teste',
  email: 'aluno@teste.local',
  provider: 'password',
  accessStatus: 'APROVADO',
  role: 'USUARIO',
};

export function apiFalsa(sobrescritas: Partial<Record<keyof Api, jest.Mock>> = {}) {
  const base: Partial<Record<keyof Api, jest.Mock>> = {
    getAppVersion: jest.fn().mockResolvedValue({ minimumVersion: null }),
    getAccount: jest.fn().mockResolvedValue(CONTA),
    getDisclaimer: jest
      .fn()
      .mockResolvedValue({ accepted: true, currentVersion: '1', acceptedVersion: '1' }),
    acceptDisclaimer: jest.fn().mockResolvedValue(undefined),
    mobileLoginWithPassword: jest.fn().mockResolvedValue({ token: 'token-novo' }),
    mobileLogout: jest.fn().mockResolvedValue(undefined),
    deleteAccount: jest.fn().mockResolvedValue(undefined),
    getStreak: jest.fn().mockResolvedValue({ currentStreak: 0 }),
    getStreakHistory: jest.fn().mockResolvedValue(null),
    getReviewsToday: jest.fn().mockResolvedValue({ dueCount: 0, due: [] }),
    getDiary: jest.fn().mockResolvedValue({ sessionsInMonth: 0, days: [] }),
  };
  return { ...base, ...sobrescritas } as unknown as Api & Record<keyof Api, jest.Mock>;
}

export function cofreFalso(inicial: string | null = 'token-guardado'): TokenStore & {
  atual: () => string | null;
} {
  let valor = inicial;
  return {
    get: jest.fn(() => Promise.resolve(valor)),
    set: jest.fn((token: string) => {
      valor = token;
      return Promise.resolve();
    }),
    clear: jest.fn(() => {
      valor = null;
      return Promise.resolve();
    }),
    atual: () => valor,
  };
}

export async function comProvedores(
  elemento: ReactElement,
  { api = apiFalsa(), tokens = cofreFalso() }: { api?: Api; tokens?: TokenStore } = {},
) {
  return await render(
    <ApiProvider api={api}>
      <SessionProvider tokens={tokens}>{elemento}</SessionProvider>
    </ApiProvider>,
  );
}

export function erroDaApi(status: number, code: string, message: string) {
  return new ApiError(status, code, message);
}
