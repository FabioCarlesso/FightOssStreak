import type { UsageEventRequest } from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Link, MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useUsageTracking } from './usage.ts';

/**
 * A coleta de uso vista do navegador (#84, D50).
 *
 * O que estes testes protegem são as duas promessas que o desenho fez: **nada além do combinado
 * sai daqui** — a query string é descartada fora dos três `utm_*`, e o referrer vira host — e
 * **nada quebra tela**, nem quando a chamada falha.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    recordUsage: vi.fn<(event: UsageEventRequest) => Promise<void>>(),
  },
}));

vi.mock('../api/client.ts', () => ({ api: apiMock }));

function App() {
  useUsageTracking();
  return (
    <Routes>
      <Route path="/" element={<Link to="/arvore">Árvore</Link>} />
      <Route path="/arvore" element={<p>árvore</p>} />
    </Routes>
  );
}

function renderEm(rota: string) {
  return render(
    <MemoryRouter initialEntries={[rota]}>
      <App />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  apiMock.recordUsage.mockResolvedValue(undefined);
});

describe('coleta de uso no navegador', () => {
  it('registra a rota de entrada', async () => {
    renderEm('/');

    await waitFor(() => expect(apiMock.recordUsage).toHaveBeenCalledTimes(1));
    expect(apiMock.recordUsage).toHaveBeenCalledWith(expect.objectContaining({ caminho: '/' }));
  });

  it('registra cada navegação, uma vez por rota', async () => {
    renderEm('/');
    await waitFor(() => expect(apiMock.recordUsage).toHaveBeenCalledTimes(1));

    await userEvent.click(screen.getByRole('link', { name: /árvore/i }));

    await waitFor(() => expect(apiMock.recordUsage).toHaveBeenCalledTimes(2));
    expect(apiMock.recordUsage).toHaveBeenLastCalledWith(
      expect.objectContaining({ caminho: '/arvore' }),
    );
  });

  it('manda os três utm_*, e descarta o resto da query string', async () => {
    renderEm('/?utm_source=whatsapp&utm_medium=link&utm_campaign=turma&token=segredo');

    await waitFor(() => expect(apiMock.recordUsage).toHaveBeenCalledTimes(1));
    const enviado = apiMock.recordUsage.mock.calls[0]?.[0];
    expect(enviado).toMatchObject({
      caminho: '/',
      utmSource: 'whatsapp',
      utmMedium: 'link',
      utmCampaign: 'turma',
    });
    // O que não foi combinado não sai daqui — nem o nome do parâmetro, nem o valor.
    expect(JSON.stringify(enviado)).not.toContain('segredo');
    expect(JSON.stringify(enviado)).not.toContain('token');
  });

  it('a coleta que falha não quebra a tela', async () => {
    apiMock.recordUsage.mockRejectedValue(new Error('sem rede'));

    renderEm('/');

    await waitFor(() => expect(apiMock.recordUsage).toHaveBeenCalled());
    expect(screen.getByRole('link', { name: /árvore/i })).toBeTruthy();
  });
});
