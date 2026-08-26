import type { AccountView } from '@fos/types';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AccountContext } from '../state/account.ts';
import { AccountPage } from './AccountPage.tsx';

/**
 * O que a tela da conta **diz** sobre por onde a pessoa entrou.
 *
 * Existe por um motivo só, e é o mesmo da D29: o campo vinha do banco direto para a tela, e desde
 * o cadastro aberto (D47) a maioria das contas passou a ler "Entrou por: password" — id interno
 * onde deveria haver uma frase.
 */
function renderCom(account: Partial<AccountView>) {
  return render(
    <AccountContext.Provider
      value={{
        account: {
          displayName: 'Ana',
          email: 'ana@example.test',
          provider: 'password',
          accessStatus: 'APROVADO',
          role: 'USUARIO',
          demoExpiresAt: null,
          ...account,
        } as AccountView,
        reload: () => {},
      }}
    >
      <AccountPage />
    </AccountContext.Provider>,
  );
}

describe('sua conta', () => {
  it('diz o provedor com o nome que a pessoa reconhece, não o id interno', () => {
    renderCom({ provider: 'password' });

    expect(screen.getByText('e-mail e senha')).toBeInTheDocument();
    expect(screen.queryByText('password')).not.toBeInTheDocument();
  });

  it('nomeia o provedor externo pelo nome dele', () => {
    renderCom({ provider: 'google' });

    expect(screen.getByText('Google')).toBeInTheDocument();
  });

  it('id desconhecido aparece como veio — ainda diz mais que um traço', () => {
    renderCom({ provider: 'provedor-novo' });

    expect(screen.getByText('provedor-novo')).toBeInTheDocument();
  });

  it('na demonstração não há provedor nenhum a nomear', () => {
    renderCom({ demoExpiresAt: '2026-08-25T23:00:00Z', provider: 'demo' });

    expect(screen.getByText('demonstração pública')).toBeInTheDocument();
    expect(screen.getByText(/a demonstração não pede e-mail/i)).toBeInTheDocument();
  });
});
