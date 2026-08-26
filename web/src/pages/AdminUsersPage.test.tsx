import type { AdminUsersQuery } from '@fos/api-client';
import type { AccessStatus, AccountView, AdminUserPage, AdminUserView, Role } from '@fos/types';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountContext } from '../state/account.ts';
import { AdminUsersPage } from './AdminUsersPage.tsx';

/**
 * A tela de Usuários (#91).
 *
 * É a primeira tela do app que mostra dado pessoal de outras pessoas e a primeira que age sobre a
 * conta de outra pessoa, e é isso que os testes daqui protegem: quem não administra não entra nem
 * pela URL; nenhuma ação sai sem confirmação; e a confirmação diz **de quem** é a conta. O resto —
 * filtro, busca, paginação — é testado pelo que vai na consulta, porque quem filtra é o backend
 * (#89) e filtrar no cliente daria a impressão errada de que a lista inteira está na tela.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getAdminUsers: vi.fn<(query?: AdminUsersQuery) => Promise<AdminUserPage>>(),
    setAdminUserRole: vi.fn<(id: number, role: Role) => Promise<AdminUserView>>(),
    setAdminUserStatus:
      vi.fn<(id: number, status: AccessStatus, motivo?: string) => Promise<AdminUserView>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const { ApiError } = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');

const ANA: AdminUserView = {
  id: 1,
  label: 'Ana',
  email: 'ana@example.test',
  emailVerified: true,
  providers: ['password'],
  role: 'USUARIO',
  accessStatus: 'APROVADO',
  createdAt: '2026-08-19T12:00:00Z',
};

const BRUNO: AdminUserView = {
  id: 2,
  label: 'Bruno',
  email: 'bruno@example.test',
  emailVerified: false,
  providers: ['google'],
  role: 'USUARIO',
  accessStatus: 'RECUSADO',
  createdAt: '2026-08-18T12:00:00Z',
};

const CARLA: AdminUserView = {
  id: 3,
  label: 'Carla',
  email: 'carla@example.test',
  emailVerified: true,
  providers: ['google', 'password'],
  role: 'ADMIN',
  accessStatus: 'APROVADO',
  createdAt: '2026-08-17T12:00:00Z',
};

const PAGINA: AdminUserPage = {
  items: [ANA, BRUNO, CARLA],
  page: 0,
  size: 20,
  total: 42,
  totalPages: 3,
};

const AGENDA = 'a agenda de hoje';

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

/**
 * O portão desta tela é um redirecionamento, então o teste precisa de um roteador com o destino —
 * é ele que prova que quem não administra sai daqui.
 */
function renderPage(account: AccountView = conta()) {
  return render(
    <AccountContext.Provider value={{ account, reload: vi.fn() }}>
      <MemoryRouter initialEntries={['/usuarios']}>
        <Routes>
          <Route path="/usuarios" element={<AdminUsersPage />} />
          <Route path="/hoje" element={<p>{AGENDA}</p>} />
        </Routes>
      </MemoryRouter>
    </AccountContext.Provider>,
  );
}

function linhaDe(email: string) {
  return screen.getByRole('row', { name: new RegExp(email.replace(/\./g, '\\.')) });
}

function ultimaConsulta(): AdminUsersQuery {
  return apiMock.getAdminUsers.mock.calls.at(-1)?.[0] ?? {};
}

beforeEach(() => {
  vi.clearAllMocks();
  apiMock.getAdminUsers.mockResolvedValue(PAGINA);
  apiMock.setAdminUserRole.mockResolvedValue({ ...ANA, role: 'ADMIN' });
  apiMock.setAdminUserStatus.mockResolvedValue({ ...ANA, accessStatus: 'RECUSADO' });
});

describe('quem pode abrir a tela', () => {
  it('conta comum é mandada para a agenda, mesmo digitando a URL', async () => {
    renderPage(conta({ role: 'USUARIO' }));

    expect(await screen.findByText(AGENDA)).toBeInTheDocument();
    // O backend recusaria de qualquer jeito; o que não pode acontecer é a tela pedir a lista.
    expect(apiMock.getAdminUsers).not.toHaveBeenCalled();
  });

  it('quem administra vê a lista', async () => {
    renderPage();

    expect(await screen.findByText('ana@example.test')).toBeInTheDocument();
    expect(apiMock.getAdminUsers).toHaveBeenCalledTimes(1);
  });
});

describe('a tabela de contas', () => {
  it('mostra rótulo, e-mail, provedores, verificação, papel, estado e criação', async () => {
    renderPage();

    const linha = within(await screen.findByRole('row', { name: /carla@example\.test/ }));
    expect(linha.getByText('Carla')).toBeInTheDocument();
    expect(linha.getByText('carla@example.test')).toBeInTheDocument();
    expect(linha.getByText('Google · e-mail e senha')).toBeInTheDocument();
    expect(linha.getByText('Confirmado')).toBeInTheDocument();
    expect(linha.getByText('Administração')).toBeInTheDocument();
    expect(linha.getByText('Ativa')).toBeInTheDocument();
    expect(linha.getByText('17/08/2026')).toBeInTheDocument();
  });

  it('não mostra o id de banco em lugar nenhum da tela', async () => {
    renderPage();

    await screen.findByText('ana@example.test');
    // O `id` existe para as chamadas; quem lê a tela lê e-mail e rótulo (#87).
    const tabela = screen.getByRole('table');
    expect(within(tabela).queryByText('1')).not.toBeInTheDocument();
    expect(within(tabela).queryByText('#1')).not.toBeInTheDocument();
  });

  it('a conta bloqueada aparece como bloqueada, e a não confirmada como não confirmada', async () => {
    renderPage();

    const linha = within(await screen.findByRole('row', { name: /bruno@example\.test/ }));
    expect(linha.getByText('Bloqueada')).toBeInTheDocument();
    expect(linha.getByText('Não confirmado')).toBeInTheDocument();
  });
});

describe('busca, filtros e paginação', () => {
  it('a busca vai para o backend, não é filtrada aqui', async () => {
    renderPage();
    await screen.findByText('ana@example.test');

    await userEvent.type(screen.getByLabelText('Buscar'), 'bru');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() => expect(apiMock.getAdminUsers).toHaveBeenCalledTimes(2));
    expect(ultimaConsulta()).toMatchObject({ busca: 'bru', page: 0 });
  });

  it('cada filtro entra na consulta com o valor da #89', async () => {
    renderPage();
    await screen.findByText('ana@example.test');

    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'RECUSADO');
    await waitFor(() => expect(ultimaConsulta()).toMatchObject({ status: 'RECUSADO' }));

    await userEvent.selectOptions(screen.getByLabelText('Papel'), 'ADMIN');
    await waitFor(() => expect(ultimaConsulta()).toMatchObject({ role: 'ADMIN' }));

    // Tri-estado: o `<select>` fala em texto, a API fala em booleano.
    await userEvent.selectOptions(screen.getByLabelText('E-mail'), 'nao');
    await waitFor(() =>
      expect(ultimaConsulta()).toMatchObject({
        status: 'RECUSADO',
        role: 'ADMIN',
        verificado: false,
      }),
    );
  });

  it('trocar o filtro volta para a primeira página', async () => {
    renderPage();
    await screen.findByText('ana@example.test');

    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }));
    await waitFor(() => expect(ultimaConsulta()).toMatchObject({ page: 1 }));

    await userEvent.selectOptions(screen.getByLabelText('Papel'), 'ADMIN');

    // A página 2 do filtro anterior não é a página 2 deste — e pode nem existir.
    await waitFor(() => expect(ultimaConsulta()).toMatchObject({ role: 'ADMIN', page: 0 }));
  });

  it('a paginação navega mantendo filtro e busca aplicados', async () => {
    renderPage();
    await screen.findByText('ana@example.test');

    await userEvent.selectOptions(screen.getByLabelText('Estado'), 'APROVADO');
    await userEvent.type(screen.getByLabelText('Buscar'), 'exam');
    await userEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    await waitFor(() => expect(ultimaConsulta()).toMatchObject({ busca: 'exam' }));

    await userEvent.click(screen.getByRole('button', { name: 'Próxima' }));

    await waitFor(() =>
      expect(ultimaConsulta()).toMatchObject({ status: 'APROVADO', busca: 'exam', page: 1 }),
    );
    expect(screen.getByText(/Página 2 de 3/)).toBeInTheDocument();
  });

  it('na primeira página não há para onde voltar', async () => {
    renderPage();
    await screen.findByText('ana@example.test');

    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
  });
});

describe('ações sobre uma conta', () => {
  it('bloquear só chama a API depois da confirmação, que diz o e-mail', async () => {
    renderPage();
    await screen.findByText('ana@example.test');

    await userEvent.click(
      screen.getByRole('button', { name: 'Bloquear a conta ana@example.test' }),
    );

    // O clique abre a conversa; ele não executa nada.
    expect(apiMock.setAdminUserStatus).not.toHaveBeenCalled();
    const dialogo = within(screen.getByRole('dialog'));
    expect(dialogo.getByText('ana@example.test')).toBeInTheDocument();

    await userEvent.type(dialogo.getByLabelText(/motivo/i), 'spam na fila de feedback');
    await userEvent.click(dialogo.getByRole('button', { name: 'Bloquear conta' }));

    expect(apiMock.setAdminUserStatus).toHaveBeenCalledWith(
      1,
      'RECUSADO',
      'spam na fila de feedback',
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(within(linhaDe('ana@example.test')).getByText('Bloqueada')).toBeInTheDocument();
  });

  it('cancelar fecha o diálogo sem tocar na conta', async () => {
    renderPage();
    await screen.findByText('ana@example.test');

    await userEvent.click(
      screen.getByRole('button', { name: 'Bloquear a conta ana@example.test' }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(apiMock.setAdminUserStatus).not.toHaveBeenCalled();
    expect(within(linhaDe('ana@example.test')).getByText('Ativa')).toBeInTheDocument();
  });

  it('desbloquear devolve o acesso à conta bloqueada', async () => {
    apiMock.setAdminUserStatus.mockResolvedValue({ ...BRUNO, accessStatus: 'APROVADO' });
    renderPage();
    await screen.findByText('bruno@example.test');

    await userEvent.click(
      screen.getByRole('button', { name: 'Desbloquear a conta bruno@example.test' }),
    );
    const dialogo = within(screen.getByRole('dialog'));
    expect(dialogo.getByText('bruno@example.test')).toBeInTheDocument();
    await userEvent.click(dialogo.getByRole('button', { name: 'Desbloquear conta' }));

    expect(apiMock.setAdminUserStatus).toHaveBeenCalledWith(2, 'APROVADO', undefined);
    await waitFor(() =>
      expect(within(linhaDe('bruno@example.test')).getByText('Ativa')).toBeInTheDocument(),
    );
  });

  it('promover só chama a API depois da confirmação, que diz o e-mail', async () => {
    renderPage();
    await screen.findByText('ana@example.test');

    await userEvent.click(
      screen.getByRole('button', { name: 'Promover a conta ana@example.test' }),
    );

    expect(apiMock.setAdminUserRole).not.toHaveBeenCalled();
    const dialogo = within(screen.getByRole('dialog'));
    expect(dialogo.getByText('ana@example.test')).toBeInTheDocument();
    await userEvent.click(dialogo.getByRole('button', { name: 'Promover a administrador' }));

    expect(apiMock.setAdminUserRole).toHaveBeenCalledWith(1, 'ADMIN');
    await waitFor(() =>
      expect(within(linhaDe('ana@example.test')).getByText('Administração')).toBeInTheDocument(),
    );
  });

  it('rebaixar só chama a API depois da confirmação, que diz o e-mail', async () => {
    apiMock.setAdminUserRole.mockResolvedValue({ ...CARLA, role: 'USUARIO' });
    renderPage();
    await screen.findByText('carla@example.test');

    await userEvent.click(
      screen.getByRole('button', { name: 'Rebaixar a conta carla@example.test' }),
    );

    expect(apiMock.setAdminUserRole).not.toHaveBeenCalled();
    const dialogo = within(screen.getByRole('dialog'));
    expect(dialogo.getByText('carla@example.test')).toBeInTheDocument();
    await userEvent.click(dialogo.getByRole('button', { name: 'Rebaixar para conta comum' }));

    expect(apiMock.setAdminUserRole).toHaveBeenCalledWith(3, 'USUARIO');
    await waitFor(() =>
      expect(within(linhaDe('carla@example.test')).getByText('Comum')).toBeInTheDocument(),
    );
  });
});

describe('conflitos do backend (409)', () => {
  it.each<[string, RegExp]>([
    ['admin_acao_sobre_si', /sua própria conta/i],
    ['admin_ultimo_admin', /última conta de administração/i],
    ['admin_email_nao_verificado', /ainda não confirmou o e-mail/i],
    ['admin_conta_de_demonstracao', /conta de demonstração/i],
  ])('%s vira frase legível e a linha não muda de estado', async (code, frase) => {
    apiMock.setAdminUserStatus.mockRejectedValue(new ApiError(409, code, 'mensagem do servidor'));
    renderPage();
    await screen.findByText('ana@example.test');

    await userEvent.click(
      screen.getByRole('button', { name: 'Bloquear a conta ana@example.test' }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Bloquear conta' }));

    expect(await screen.findByText(frase)).toBeInTheDocument();
    // Nada mudou no servidor; mostrar outra coisa aqui seria mentira.
    expect(within(linhaDe('ana@example.test')).getByText('Ativa')).toBeInTheDocument();
  });
});

describe('lista vazia e falha de carregamento', () => {
  it('nenhuma conta com o filtro não parece erro', async () => {
    apiMock.getAdminUsers.mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      total: 0,
      totalPages: 0,
    });
    renderPage();

    expect(await screen.findByText(/nenhuma conta com esses filtros/i)).toBeInTheDocument();
    expect(screen.queryByText(/não foi possível carregar/i)).not.toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('falha de rede diz que falhou e oferece tentar de novo', async () => {
    apiMock.getAdminUsers.mockRejectedValueOnce(new Error('Failed to fetch'));
    renderPage();

    expect(await screen.findByText(/não foi possível carregar as contas/i)).toBeInTheDocument();
    // Lista vazia pede outro filtro; falha pede outra tentativa. Confundir as duas manda a pessoa
    // procurar o problema no lugar errado.
    expect(screen.queryByText(/nenhuma conta com esses filtros/i)).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /tentar de novo/i }));

    expect(await screen.findByText('ana@example.test')).toBeInTheDocument();
  });
});
