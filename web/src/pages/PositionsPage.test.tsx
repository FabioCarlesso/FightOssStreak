import type { TreeView } from '@fos/types';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DemoModeProvider } from '../state/DemoModeProvider.tsx';
import { PositionsPage } from './PositionsPage.tsx';

/**
 * O mapa de posições (#194, D76) é outro corte da mesma árvore: o que importa aqui é que cada
 * técnica apareça sob a posição de onde parte, com o mesmo status da árvore, e que posição sem
 * técnica diga isso em vez de sumir.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: { getTree: vi.fn<() => Promise<TreeView>>() },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const ARVORE: TreeView = {
  modules: [
    {
      code: 'M3',
      title: 'Guarda aberta',
      nodes: [
        {
          code: 'M3.6',
          title: 'Meia-guarda: conceito',
          status: 'COMPLETED',
          positionCodes: ['MEIA_GUARDA'],
        },
        {
          code: 'M3.7',
          title: 'Raspagem de meia-guarda',
          status: 'LOCKED',
          prereqCodes: ['M3.6'],
          positionCodes: ['MEIA_GUARDA'],
        },
      ],
    },
  ],
  positions: [
    {
      code: 'MEIA_GUARDA',
      name: 'Meia-guarda',
      aliases: ['half guard'],
      family: 'GUARDA',
      perspective: 'BAIXO',
      identify: 'Quem está por baixo prende uma só perna do oponente entre as suas.',
    },
    {
      code: 'NORTE_SUL',
      name: 'Norte-sul',
      aliases: [],
      family: 'CONTROLE_POR_CIMA',
      perspective: 'CIMA',
      identify: 'Os dois ficam alinhados em sentidos opostos, peito com peito.',
    },
  ],
};

beforeEach(() => {
  vi.clearAllMocks();
  sessionStorage.clear();
  apiMock.getTree.mockResolvedValue(ARVORE);
});

function renderPage() {
  return render(
    <MemoryRouter>
      <DemoModeProvider>
        <PositionsPage />
      </DemoModeProvider>
    </MemoryRouter>,
  );
}

describe('PositionsPage', () => {
  it('agrupa por família e mostra como reconhecer, os apelidos e as técnicas com status', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Guardas' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Controle por cima' })).toBeInTheDocument();

    const meia = screen.getByRole('heading', { name: 'Meia-guarda' }).closest('li')!;
    expect(within(meia).getByText(/prende uma só perna/)).toBeInTheDocument();
    expect(within(meia).getByText(/também chamada de half guard/i)).toBeInTheDocument();
    // Concluído é link para o nó; bloqueado continua bloqueado, como na árvore.
    expect(within(meia).getByRole('link', { name: /M3\.6/ })).toHaveAttribute('href', '/no/M3.6');
    expect(within(meia).queryByRole('link', { name: /M3\.7/ })).not.toBeInTheDocument();
    expect(within(meia).getByText(/requer: M3\.6/i)).toBeInTheDocument();
  });

  it('posição sem técnica no currículo diz isso, em vez de sumir', async () => {
    renderPage();

    const norteSul = (await screen.findByRole('heading', { name: 'Norte-sul' })).closest('li')!;
    expect(within(norteSul).getByText(/ainda não há técnica no currículo/i)).toBeInTheDocument();
  });
});
