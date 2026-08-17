import type { TreeView } from '@fos/types';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DemoModeProvider } from '../state/DemoModeProvider.tsx';
import { TreePage } from './TreePage.tsx';

/**
 * O modo demonstração (D31) inverte a única coisa que a árvore decide: se o nó é clicável. É uma
 * extensão consciente da D29, que delimitou os testes de UI a três fluxos — o risco aqui não é de
 * layout, é de a demonstração vazar para o modo normal (nó bloqueado virando link sem que ninguém
 * tenha ligado nada) ou de o modo não desligar.
 *
 * O cliente de API é mockado no módulo: nenhum teste toca a rede.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getTree: vi.fn<() => Promise<TreeView>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const ARVORE: TreeView = {
  summary: { totalNodes: 3, completedNodes: 1, availableNodes: 1, lockedNodes: 1 },
  modules: [
    {
      code: 'M0',
      title: 'Fundamentos',
      summary: 'Antes de qualquer técnica.',
      nodes: [
        { code: 'M0.1', title: 'Postura', belt: 'BRANCA', status: 'COMPLETED' },
        { code: 'M0.2', title: 'Fuga de quadril', belt: 'BRANCA', status: 'AVAILABLE' },
        {
          code: 'M0.3',
          title: 'Ponte',
          belt: 'BRANCA',
          status: 'LOCKED',
          unlockRule: 'ALL',
          prereqCodes: ['M0.2'],
        },
      ],
    },
  ],
};

const RESUMO_REAL = '1 concluídos · 1 disponíveis · 1 bloqueados · 3 nós no total';

function renderTree() {
  return render(
    <DemoModeProvider>
      <MemoryRouter>
        <TreePage />
      </MemoryRouter>
    </DemoModeProvider>,
  );
}

/** O nó só é acessível quando a linha inteira vira link — é essa a diferença entre os dois modos. */
function acharLink(titulo: RegExp) {
  return screen.findByRole('link', { name: titulo });
}

function semLink(titulo: RegExp) {
  return screen.queryByRole('link', { name: titulo });
}

function botaoDemo() {
  return screen.findByRole('button', { name: /demonstração/i });
}

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  apiMock.getTree.mockResolvedValue(ARVORE);
});

describe('TreePage', () => {
  it('com o modo desligado, nó bloqueado não é link e mostra o pré-requisito', async () => {
    renderTree();

    expect(await acharLink(/fuga de quadril/i)).toBeInTheDocument();
    expect(semLink(/ponte/i)).not.toBeInTheDocument();
    expect(screen.getByText('Requer: M0.2')).toBeInTheDocument();
    expect(await botaoDemo()).toHaveAttribute('aria-pressed', 'false');
  });

  it('ligar a demonstração torna o nó bloqueado clicável, sem esconder o pré-requisito', async () => {
    renderTree();
    await userEvent.click(await botaoDemo());

    expect(await acharLink(/ponte/i)).toHaveAttribute('href', '/no/M0.3');
    expect(semLink(/fuga de quadril/i)).toBeInTheDocument();
    // A dica continua: com o modo ligado ela é informação, não portão.
    expect(screen.getByText('Requer: M0.2')).toBeInTheDocument();
    expect(await botaoDemo()).toHaveAttribute('aria-pressed', 'true');
  });

  it('desligar restaura o bloqueio integralmente', async () => {
    renderTree();
    const botao = await botaoDemo();

    await userEvent.click(botao);
    expect(await acharLink(/ponte/i)).toBeInTheDocument();

    await userEvent.click(botao);
    expect(semLink(/ponte/i)).not.toBeInTheDocument();
    expect(screen.getByText('Requer: M0.2')).toBeInTheDocument();
  });

  it('os contadores mostram o progresso real, com o modo ligado ou desligado', async () => {
    // São eles a referência do que ainda está travado de verdade; reescrevê-los no modo
    // demonstração apagaria a única leitura confiável da tela.
    renderTree();
    expect(await screen.findByText(/nós no total/)).toHaveTextContent(RESUMO_REAL);

    await userEvent.click(await botaoDemo());

    expect(screen.getByText(/nós no total/)).toHaveTextContent(RESUMO_REAL);
  });

  it('o modo sobrevive ao recarregamento da página, e não à aba nova', async () => {
    const primeira = renderTree();
    await userEvent.click(await botaoDemo());
    primeira.unmount();

    // F5: a árvore é montada de novo e o modo volta do `sessionStorage`.
    const segunda = renderTree();
    expect(await acharLink(/ponte/i)).toBeInTheDocument();
    segunda.unmount();

    // Aba nova começa com o `sessionStorage` vazio — e o nó volta a ser bloqueado.
    sessionStorage.clear();
    renderTree();
    expect(await acharLink(/fuga de quadril/i)).toBeInTheDocument();
    expect(semLink(/ponte/i)).not.toBeInTheDocument();
  });
});
