import type { HealthHours } from '@fos/api-client';
import type { HealthView } from '@fos/types';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SiteHealth } from './SiteHealth.tsx';

/**
 * A seção de saúde do painel (#86).
 *
 * O que os testes protegem aqui é o que a tela **afirma**, não como ela desenha. Três coisas custam
 * caro quando faltam:
 *
 * - a tela precisa dizer que **não sabe** se o site esteve fora do ar, senão o administrador lê
 *   "99,9% de disponibilidade" numa manhã em que o app passou duas horas morto;
 * - "sem medição ainda" e "medimos, e deu zero" são conclusões opostas e não podem se parecer;
 * - o p95 tem **três** leituras — sem medição, dentro de uma faixa, e acima da escada inteira — e
 *   confundir a primeira com um número é o pior erro possível num painel.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getAdminHealth: vi.fn<(horas?: HealthHours) => Promise<HealthView>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

function saude(overrides: Partial<HealthView> = {}): HealthView {
  return {
    hours: 24,
    from: '2026-08-26T11:00:00Z',
    to: '2026-08-27T10:00:00Z',
    collectedThrough: '2026-08-27T10:00:00Z',
    requests: 100,
    serverErrors: 4,
    clientErrors: 7,
    availabilityPercent: 96,
    p95Ms: 250,
    latencyCeilingMs: 2500,
    hourly: [
      { hour: '2026-08-27T09:00:00Z', requests: 40, serverErrors: 0, clientErrors: 2 },
      { hour: '2026-08-27T10:00:00Z', requests: 60, serverErrors: 4, clientErrors: 5 },
    ],
    routes: [
      {
        path: '/api/nodes/{code}',
        requests: 10,
        serverErrors: 4,
        errorPercent: 40,
        p95Ms: 1000,
        avgMs: 320,
        maxMs: 900,
      },
    ],
    slowest: [
      {
        path: '/api/curriculum/tree',
        requests: 90,
        serverErrors: 0,
        errorPercent: 0,
        p95Ms: 500,
        avgMs: 120,
        maxMs: 700,
      },
    ],
    startsInPeriod: 1,
    starts: [{ startedAt: '2026-08-27T09:12:00Z', profiles: 'postgres' }],
    ...overrides,
  };
}

describe('SiteHealth', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apiMock.getAdminHealth.mockResolvedValue(saude());
  });

  it('mostra disponibilidade, erros, p95 e subidas do período', async () => {
    render(<SiteHealth />);

    const cartao = (await screen.findByRole('heading', { name: 'Saúde' })).closest(
      'section',
    ) as HTMLElement;

    expect(within(cartao).getByText('96.0%')).toBeInTheDocument();
    expect(within(cartao).getByText('4')).toBeInTheDocument();
    expect(within(cartao).getByText('≤ 250 ms')).toBeInTheDocument();
    expect(within(cartao).getByText('postgres')).toBeInTheDocument();
  });

  it('diz que não sabe se o site ficou fora do ar', async () => {
    render(<SiteHealth />);

    // A frase é o produto, não enfeite: sem ela a disponibilidade é lida como tempo de pé, e o app
    // parado — que não mede nada — apareceria como 100%.
    expect(
      await screen.findByText(/não sabe dizer se o site esteve fora do ar/),
    ).toBeInTheDocument();
  });

  it('separa "ainda não medimos" de "medimos e deu zero"', async () => {
    apiMock.getAdminHealth.mockResolvedValue(saude({ collectedThrough: undefined }));

    render(<SiteHealth />);

    expect(await screen.findByText(/ainda não há medição nenhuma/i)).toBeInTheDocument();
    // O ranking não pode aparecer zerado ao lado disso: seria afirmar que nenhuma rota errou.
    expect(screen.queryByText('Rotas que erraram')).not.toBeInTheDocument();
  });

  it('rota sem erro nenhum tem estado próprio, e não lista vazia', async () => {
    apiMock.getAdminHealth.mockResolvedValue(saude({ routes: [] }));

    render(<SiteHealth />);

    expect(await screen.findByText('Nenhuma rota respondeu 5xx nesta janela.')).toBeInTheDocument();
  });

  it('o p95 tem leitura própria sem medição e acima da escada', async () => {
    apiMock.getAdminHealth.mockResolvedValue(saude({ p95Ms: -1 }));
    const { unmount } = render(<SiteHealth />);
    expect(await screen.findByText('sem medição')).toBeInTheDocument();
    unmount();

    // Zero é a faixa de cima — o caso MAIS lento de todos. Lido como número, seria o mais rápido.
    apiMock.getAdminHealth.mockResolvedValue(saude({ p95Ms: 0 }));
    render(<SiteHealth />);
    expect(await screen.findByText('> 2500 ms')).toBeInTheDocument();
  });

  it('troca de janela refaz a consulta com o preset pedido', async () => {
    render(<SiteHealth />);
    await screen.findByRole('heading', { name: 'Saúde' });

    await userEvent.click(screen.getByRole('button', { name: '7 dias' }));

    await waitFor(() => expect(apiMock.getAdminHealth).toHaveBeenLastCalledWith(168));
    expect(screen.getByRole('button', { name: '7 dias' })).toHaveAttribute('aria-pressed', 'true');
  });
});
