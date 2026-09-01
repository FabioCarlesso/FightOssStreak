import type { HealthHours, PanelDays } from '@fos/api-client';
import type { AccountView, HealthView, PanelView } from '@fos/types';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountContext } from '../state/account.ts';
import { AdminPanelPage } from './AdminPanelPage.tsx';

/**
 * O painel do administrador (#85).
 *
 * O que os testes daqui protegem é a fronteira, não a aparência: **quem não administra não entra**
 * nem pela URL; **os seis degraus do funil aparecem sempre**, inclusive zerados; e os três estados
 * vazios — nunca agregou, período sem acesso, dimensão sem linha — são **distinguíveis na tela**.
 * Esse último é o que mais custa quando falta: um zero que pode significar "ninguém veio" ou "o job
 * não rodou" faz o administrador procurar defeito onde não há.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getAdminPanel: vi.fn<(dias?: PanelDays) => Promise<PanelView>>(),
    // A seção de saúde (#86) mora nesta página e carrega o próprio dado. Ela precisa estar aqui
    // mesmo que nenhum caso teste saúde: sem o mock, a página inteira quebra no render.
    getAdminHealth: vi.fn<(horas?: HealthHours) => Promise<HealthView>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const AGENDA = 'a agenda de hoje';

function painel(overrides: Partial<PanelView> = {}): PanelView {
  return {
    days: 7,
    from: '2026-08-20',
    to: '2026-08-26',
    previousFrom: '2026-08-13',
    previousTo: '2026-08-19',
    aggregatedThrough: '2026-08-26',
    access: {
      series: [
        { day: '2026-08-25', visits: 12, visitors: 5 },
        { day: '2026-08-26', visits: 40, visitors: 20 },
      ],
      visits: 52,
      visitors: 25,
      previousVisits: 26,
      previousVisitors: 20,
    },
    funnel: [
      { step: 'VISITA', label: 'Visitaram', total: 25 },
      {
        step: 'DEMONSTRACAO_ABERTA',
        label: 'Abriram a demonstração',
        total: 5,
        percentOfPrevious: 20,
      },
      { step: 'CADASTRO_CRIADO', label: 'Criaram conta', total: 2, percentOfPrevious: 40 },
      { step: 'EMAIL_VERIFICADO', label: 'Confirmaram o e-mail', total: 1, percentOfPrevious: 50 },
      {
        step: 'PRIMEIRO_DRILL',
        label: 'Registraram o primeiro drill',
        total: 0,
        percentOfPrevious: 0,
      },
      { step: 'RETORNO_EM_7_DIAS', label: 'Voltaram em 7 dias', total: 0 },
    ],
    origins: [
      { value: 'direto', total: 30, visitors: 15 },
      { value: 'whatsapp', total: 22, visitors: 10 },
    ],
    profile: {
      devices: [{ value: 'CELULAR', total: 40, visitors: 18 }],
      browsers: [{ value: 'chrome', total: 38, visitors: 17 }],
      languages: [{ value: 'pt-br', total: 45, visitors: 22 }],
      countries: [{ value: 'BR', total: 44, visitors: 21 }],
    },
    content: [
      { value: '/', total: 30, visitors: 20 },
      { value: '/no/{codigo}', total: 8, visitors: 4 },
    ],
    accounts: { total: 9, createdInPeriod: 2, activeInPeriod: 3 },
    geoIpCredit: 'Dados de país por IP: DB-IP Lite (CC BY 4.0)',
    ...overrides,
  };
}

/** A saúde não é o assunto destes casos — só precisa carregar sem erro. Ver `SiteHealth.test.tsx`. */
function saudeVazia(): HealthView {
  return {
    hours: 24,
    from: '2026-08-26T11:00:00Z',
    to: '2026-08-27T10:00:00Z',
    requests: 0,
    serverErrors: 0,
    clientErrors: 0,
    availabilityPercent: 100,
    p95Ms: -1,
    latencyCeilingMs: 2500,
    hourly: [],
    routes: [],
    slowest: [],
    startsInPeriod: 0,
    starts: [],
  };
}

function conta(overrides: Partial<AccountView> = {}): AccountView {
  return {
    displayName: 'Dono',
    email: 'dono@example.test',
    provider: 'password',
    accessStatus: 'APROVADO',
    role: 'ADMIN',
    ...overrides,
  };
}

/** O portão desta tela é um redirecionamento, então o teste precisa do roteador com o destino. */
function renderPage(account: AccountView = conta()) {
  return render(
    <AccountContext.Provider value={{ account, reload: vi.fn() }}>
      <MemoryRouter initialEntries={['/admin/painel']}>
        <Routes>
          <Route path="/admin/painel" element={<AdminPanelPage />} />
          <Route path="/hoje" element={<p>{AGENDA}</p>} />
        </Routes>
      </MemoryRouter>
    </AccountContext.Provider>,
  );
}

describe('AdminPanelPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apiMock.getAdminPanel.mockResolvedValue(painel());
    apiMock.getAdminHealth.mockResolvedValue(saudeVazia());
  });

  it('não abre para quem não administra, e nem chega a consultar o painel', async () => {
    renderPage(conta({ role: 'USUARIO' }));

    expect(await screen.findByText(AGENDA)).toBeInTheDocument();
    expect(apiMock.getAdminPanel).not.toHaveBeenCalled();
    expect(apiMock.getAdminHealth).not.toHaveBeenCalled();
  });

  it('mostra acessos, comparativo, perfil, conteúdo e contas', async () => {
    renderPage();

    const acessos = await screen.findByRole('heading', { name: 'Acessos' });
    const cartao = acessos.closest('section') as HTMLElement;
    expect(within(cartao).getByText('52')).toBeInTheDocument();
    expect(within(cartao).getByText('25')).toBeInTheDocument();
    // 52 contra 26 no período anterior: o dobro.
    expect(within(cartao).getByText('+100%')).toBeInTheDocument();

    expect(screen.getByText('whatsapp')).toBeInTheDocument();
    expect(screen.getByText('chrome')).toBeInTheDocument();
    expect(screen.getByText('/no/{codigo}')).toBeInTheDocument();

    const contas = screen
      .getByRole('heading', { name: 'Contas' })
      .closest('section') as HTMLElement;
    expect(within(contas).getByText('9')).toBeInTheDocument();
    expect(within(contas).getByText('Ativas no período')).toBeInTheDocument();

    // Crédito da base de geolocalização — exigência da licença CC BY 4.0 (D50).
    expect(screen.getByText(/DB-IP Lite/)).toBeInTheDocument();
  });

  it('mostra os seis degraus do funil, e o degrau zerado aparece como zero', async () => {
    renderPage();

    const funil = await screen.findByRole('heading', { name: 'Funil' });
    const itens = within(funil.closest('section') as HTMLElement).getAllByRole('listitem');

    expect(itens).toHaveLength(6);
    expect(itens[5]).toHaveTextContent('Voltaram em 7 dias');
    expect(itens[4]).toHaveTextContent('0');
    // Sem degrau anterior não há conversão a afirmar — e o traço diz isso sem inventar 0%.
    expect(itens[0]).toHaveTextContent('—');
  });

  it('troca de período refaz a consulta com o preset pedido', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'Acessos' });

    await userEvent.click(screen.getByRole('button', { name: '30 dias' }));

    await waitFor(() => expect(apiMock.getAdminPanel).toHaveBeenLastCalledWith(30));
    expect(screen.getByRole('button', { name: '30 dias' })).toHaveAttribute('aria-pressed', 'true');
  });

  it('período sem acesso tem estado próprio, e não gráfico vazio', async () => {
    apiMock.getAdminPanel.mockResolvedValue(
      painel({
        access: { series: [], visits: 0, visitors: 0, previousVisits: 0, previousVisitors: 0 },
        origins: [],
        content: [],
      }),
    );

    renderPage();

    expect(await screen.findByText('Nenhum acesso neste período.')).toBeInTheDocument();
    expect(screen.getByText('Nenhuma origem registrada neste período.')).toBeInTheDocument();
    expect(screen.getByText('Nenhuma tela aberta neste período.')).toBeInTheDocument();
    // O funil continua na tela: zerado é uma resposta, e some seria outra.
    expect(screen.getByRole('heading', { name: 'Funil' })).toBeInTheDocument();
  });

  it('diz quando a agregação ainda não fechou dia nenhum', async () => {
    apiMock.getAdminPanel.mockResolvedValue(painel({ aggregatedThrough: undefined }));

    renderPage();

    expect(await screen.findByText(/ainda não fechou nenhum dia/)).toBeInTheDocument();
  });

  it('avisa quando a agregação está atrasada em relação ao fim do período', async () => {
    apiMock.getAdminPanel.mockResolvedValue(painel({ aggregatedThrough: '2026-08-24' }));

    renderPage();

    expect(await screen.findByText(/último dia com contagem é 24\/08/)).toBeInTheDocument();
  });

  it('falha de rede oferece tentar de novo em vez de tela em branco', async () => {
    apiMock.getAdminPanel.mockRejectedValue(new Error('rede fora'));

    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('rede fora');

    apiMock.getAdminPanel.mockResolvedValue(painel());
    apiMock.getAdminHealth.mockResolvedValue(saudeVazia());
    await userEvent.click(screen.getByRole('button', { name: 'Tentar de novo' }));

    expect(await screen.findByRole('heading', { name: 'Acessos' })).toBeInTheDocument();
  });

  it('nenhum texto da tela carrega e-mail ou id de conta', async () => {
    const { container } = renderPage();
    await screen.findByRole('heading', { name: 'Acessos' });

    // A tela é agregada e de ninguém (D50): o que ela não pode mostrar não depende de qual campo o
    // backend acrescentar depois, então a asserção é sobre o texto inteiro.
    expect(container.textContent ?? '').not.toMatch(/@[\w.-]+\.\w+/);
  });
});
