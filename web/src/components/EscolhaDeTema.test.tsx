import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { runInThisContext } from 'node:vm';

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CHAVE_DO_TEMA } from '../state/tema.ts';
import { EscolhaDeTema } from './EscolhaDeTema.tsx';

/**
 * O tema (D74, #187): segue o sistema até a pessoa escolher, troca sem recarregar e a escolha
 * sobrevive a um novo acesso — só neste navegador.
 */

const raiz = document.documentElement;

beforeEach(() => {
  localStorage.clear();
  raiz.removeAttribute('data-theme');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('escolha de tema', () => {
  it('começa em Sistema, sem `data-theme` — quem decide é o prefers-color-scheme', () => {
    render(<EscolhaDeTema />);

    expect(screen.getByRole('radio', { name: 'Sistema' })).toBeChecked();
    expect(raiz).not.toHaveAttribute('data-theme');
  });

  it('troca na hora e guarda no navegador', async () => {
    render(<EscolhaDeTema />);

    await userEvent.click(screen.getByRole('radio', { name: 'Claro' }));
    expect(raiz).toHaveAttribute('data-theme', 'light');
    expect(localStorage.getItem(CHAVE_DO_TEMA)).toBe('claro');

    await userEvent.click(screen.getByRole('radio', { name: 'Escuro' }));
    expect(raiz).toHaveAttribute('data-theme', 'dark');
    expect(localStorage.getItem(CHAVE_DO_TEMA)).toBe('escuro');
  });

  it('voltar para Sistema tira o atributo e apaga a escolha', async () => {
    localStorage.setItem(CHAVE_DO_TEMA, 'claro');
    render(<EscolhaDeTema />);

    await userEvent.click(screen.getByRole('radio', { name: 'Sistema' }));
    expect(raiz).not.toHaveAttribute('data-theme');
    expect(localStorage.getItem(CHAVE_DO_TEMA)).toBeNull();
  });

  it('no novo acesso, a escolha guardada já vem marcada', () => {
    localStorage.setItem(CHAVE_DO_TEMA, 'escuro');
    render(<EscolhaDeTema />);

    expect(screen.getByRole('radio', { name: 'Escuro' })).toBeChecked();
  });

  it('sem localStorage, troca do mesmo jeito e não quebra a tela', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    render(<EscolhaDeTema />);

    expect(screen.getByRole('radio', { name: 'Sistema' })).toBeChecked();
    await userEvent.click(screen.getByRole('radio', { name: 'Claro' }));
    expect(raiz).toHaveAttribute('data-theme', 'light');
    expect(screen.getByRole('radio', { name: 'Claro' })).toBeChecked();
  });
});

/**
 * O `public/tema.js` roda antes do bundle (a CSP não deixa script inline) e precisa ler a mesma
 * chave com os mesmos valores. Ele é executado aqui como o navegador o executaria.
 */
describe('public/tema.js', () => {
  const codigo = readFileSync(resolve(__dirname, '../../public/tema.js'), 'utf8');
  const rodar = () => {
    runInThisContext(codigo);
  };

  it.each([
    ['claro', 'light'],
    ['escuro', 'dark'],
  ])('com "%s" guardado, põe data-theme="%s" antes do React', (guardado, atributo) => {
    localStorage.setItem(CHAVE_DO_TEMA, guardado);
    rodar();
    expect(raiz).toHaveAttribute('data-theme', atributo);
  });

  it('sem escolha, ou com valor estranho, deixa o sistema decidir', () => {
    rodar();
    expect(raiz).not.toHaveAttribute('data-theme');
    localStorage.setItem(CHAVE_DO_TEMA, 'roxo');
    rodar();
    expect(raiz).not.toHaveAttribute('data-theme');
  });

  it('sem localStorage, não lança', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    expect(rodar).not.toThrow();
  });
});
