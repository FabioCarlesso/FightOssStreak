import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { CHAVE_DO_TEMA } from '../state/tema.ts';
import { AtalhoDeTema } from './AtalhoDeTema.tsx';
import { EscolhaDeTema } from './EscolhaDeTema.tsx';

/** O atalho do cabeçalho (D74): troca claro/escuro e fica em sintonia com o seletor da Conta. */

const raiz = document.documentElement;

function sistemaClaro(claro: boolean) {
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({
      matches: claro,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })),
  );
}

beforeEach(() => {
  localStorage.clear();
  raiz.removeAttribute('data-theme');
  vi.unstubAllGlobals();
});

describe('atalho de tema', () => {
  it('com o sistema escuro, oferece o claro e grava a escolha', async () => {
    sistemaClaro(false);
    render(<AtalhoDeTema />);

    await userEvent.click(screen.getByRole('button', { name: 'Usar tema claro' }));

    expect(raiz).toHaveAttribute('data-theme', 'light');
    expect(localStorage.getItem(CHAVE_DO_TEMA)).toBe('claro');
    expect(screen.getByRole('button', { name: 'Usar tema escuro' })).toBeInTheDocument();
  });

  it('com o sistema claro, oferece o escuro', () => {
    sistemaClaro(true);
    render(<AtalhoDeTema />);

    expect(screen.getByRole('button', { name: 'Usar tema escuro' })).toBeInTheDocument();
  });

  it('a escolha manual vence o sistema', () => {
    sistemaClaro(true);
    localStorage.setItem(CHAVE_DO_TEMA, 'escuro');
    render(<AtalhoDeTema />);

    expect(screen.getByRole('button', { name: 'Usar tema claro' })).toBeInTheDocument();
  });

  it('trocar no cabeçalho atualiza o seletor da Conta na mesma tela', async () => {
    sistemaClaro(false);
    render(
      <>
        <AtalhoDeTema />
        <EscolhaDeTema />
      </>,
    );
    expect(screen.getByRole('radio', { name: 'Sistema' })).toBeChecked();

    await userEvent.click(screen.getByRole('button', { name: 'Usar tema claro' }));

    expect(screen.getByRole('radio', { name: 'Claro' })).toBeChecked();
  });
});
