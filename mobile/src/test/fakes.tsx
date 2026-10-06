import type { AccountView } from '@fos/types';
import { render } from '@testing-library/react-native';
import type { ReactElement } from 'react';

import { ApiError, type Api } from '../api/client';
import type { Notificador, Permissao } from '../lembretes/notificador';
import { PREFERENCIAS_PADRAO, type Lembrete, type Preferencias } from '../lembretes/plano';
import type { PreferenciasStore } from '../lembretes/preferencias';
import { ApiProvider } from '../state/api';
import { LembretesProvider } from '../state/lembretes';
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
    getTree: jest.fn().mockResolvedValue({ modules: [] }),
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

/**
 * O nativo das notificações, em memória: guarda o que foi agendado e responde a permissão que o
 * teste escolher. `respostaAoPedido` é o que a pessoa responde ao pedido do sistema.
 */
export function notificadorFalso(
  inicial: Permissao = 'indefinida',
  respostaAoPedido: Permissao = 'concedida',
) {
  let permissao = inicial;
  let agendados: Lembrete[] = [];
  const notificador = {
    permissao: jest.fn(() => Promise.resolve(permissao)),
    pedirPermissao: jest.fn(() => {
      permissao = respostaAoPedido;
      return Promise.resolve(permissao);
    }),
    cancelarTodos: jest.fn(() => {
      agendados = [];
      return Promise.resolve();
    }),
    agendar: jest.fn((lembrete: Lembrete) => {
      agendados.push(lembrete);
      return Promise.resolve();
    }),
  };
  return Object.assign(notificador as Notificador & typeof notificador, {
    agendados: () => agendados,
  });
}

export function preferenciasFalsas(inicial: Preferencias = PREFERENCIAS_PADRAO) {
  let valor = inicial;
  const store = {
    get: jest.fn(() => Promise.resolve(valor)),
    set: jest.fn((novas: Preferencias) => {
      valor = novas;
      return Promise.resolve();
    }),
  };
  return Object.assign(store as PreferenciasStore & typeof store, { atual: () => valor });
}

export async function comProvedores(
  elemento: ReactElement,
  {
    api = apiFalsa(),
    tokens = cofreFalso(),
    notificador = notificadorFalso(),
    preferencias = preferenciasFalsas(),
    agora,
  }: {
    api?: Api;
    tokens?: TokenStore;
    notificador?: Notificador;
    preferencias?: PreferenciasStore;
    agora?: () => Date;
  } = {},
) {
  return await render(
    <ApiProvider api={api}>
      <SessionProvider tokens={tokens}>
        <LembretesProvider
          notificador={notificador}
          preferenciasGuardadas={preferencias}
          agora={agora}
        >
          {elemento}
        </LembretesProvider>
      </SessionProvider>
    </ApiProvider>,
  );
}

export function erroDaApi(status: number, code: string, message: string) {
  return new ApiError(status, code, message);
}
