import { useEffect, useState } from 'react';
import { Navigate } from 'react-router-dom';
import type { AccessStatus, AdminUserView, Role } from '@fos/types';
import { ApiError, api } from '../api/client.ts';
import { useAccount } from '../state/account.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * Contas do sistema, do lado de quem administra (#91).
 *
 * É a primeira tela do app que mostra **dado pessoal de outras pessoas** e a primeira que executa
 * **ação sobre a conta de outra pessoa**. As duas coisas mudam o que a interface tem que fazer:
 * toda ação passa por um diálogo que escreve o e-mail de quem vai ser afetado, porque um clique
 * errado aqui tranca alguém para fora do app.
 *
 * O que a pessoa lê é rótulo e e-mail. O `id` existe só para as chamadas — mostrar id de banco na
 * interface foi o defeito que a #87 acabou de corrigir na tela da conta.
 *
 * Filtro, busca e paginação vão todos para o backend (#89): filtrar no cliente daria a impressão de
 * que a lista inteira está aqui, e ela não está — a página tem tamanho, e o total é do servidor.
 */
export function AdminUsersPage() {
  const { account } = useAccount();

  // Quem não administra não vê esta tela nem digitando a URL. O backend recusa de qualquer jeito;
  // o redirecionamento existe para não oferecer uma tela que só saberia dizer 403.
  if (account.role !== 'ADMIN') {
    return <Navigate to="/hoje" replace />;
  }
  return <ListaDeContas />;
}

/** Tamanho de página. O teto do backend é 100; 20 é o default de lá e cabe numa tela. */
const POR_PAGINA = 20;

interface Filtros {
  readonly status: AccessStatus | '';
  readonly role: Role | '';
  /** Tri-estado como string porque `<select>` não tem booleano — `''` é "tanto faz". */
  readonly verificado: '' | 'sim' | 'nao';
  readonly busca: string;
}

const SEM_FILTRO: Filtros = { status: '', role: '', verificado: '', busca: '' };

type Acao = 'bloquear' | 'desbloquear' | 'promover' | 'rebaixar';

interface Confirmacao {
  readonly acao: Acao;
  readonly conta: AdminUserView;
}

/**
 * O que cada ação faz, escrito para quem vai clicar.
 *
 * O efeito é dito em uma frase antes da confirmação porque bloquear e promover não são reversíveis
 * do ponto de vista de quem está do outro lado: a pessoa vai perceber, e a explicação precisa ter
 * existido antes disso.
 */
const TEXTO_DA_ACAO: Record<Acao, { titulo: string; confirmar: string; efeito: string }> = {
  bloquear: {
    titulo: 'Bloquear esta conta?',
    confirmar: 'Bloquear conta',
    efeito:
      'O acesso cai agora, inclusive nas abas que estiverem abertas. Nada é apagado, e desbloquear' +
      ' devolve a conta exatamente como estava.',
  },
  desbloquear: {
    titulo: 'Devolver o acesso a esta conta?',
    confirmar: 'Desbloquear conta',
    efeito: 'A conta volta a usar o app normalmente, do ponto em que parou.',
  },
  promover: {
    titulo: 'Promover esta conta à administração?',
    confirmar: 'Promover a administrador',
    efeito:
      'Quem administra vê o e-mail de todas as contas e pode bloquear, desbloquear e mudar papel —' +
      ' inclusive o de outras contas de administração.',
  },
  rebaixar: {
    titulo: 'Tirar a administração desta conta?',
    confirmar: 'Rebaixar para conta comum',
    efeito: 'A conta continua funcionando como qualquer outra; só deixa de administrar o app.',
  },
};

/**
 * Código de conflito virado em frase.
 *
 * Cada um dos quatro `409` leva a uma decisão diferente de quem está lendo — pedir confirmação do
 * e-mail, promover outra conta antes, escolher outra pessoa —, então cada um tem frase própria. A
 * regra continua sendo do backend; aqui só se traduz o código.
 */
function mensagemDaFalha(cause: unknown): string {
  if (cause instanceof ApiError) {
    switch (cause.code) {
      case 'admin_acao_sobre_si':
        return 'Você não pode fazer isso com a sua própria conta.';
      case 'admin_ultimo_admin':
        return 'Esta é a última conta de administração do app — promova outra antes.';
      case 'admin_email_nao_verificado':
        return 'Esta conta ainda não confirmou o e-mail, e só conta confirmada pode administrar.';
      case 'admin_conta_de_demonstracao':
        return 'Conta de demonstração não é administrada: ela é descartável e vence sozinha.';
      default:
        break;
    }
  }
  return cause instanceof Error ? cause.message : String(cause);
}

function ListaDeContas() {
  const { account } = useAccount();
  const [filtros, setFiltros] = useState<Filtros>(SEM_FILTRO);
  const [pagina, setPagina] = useState(0);
  const [rascunhoDaBusca, setRascunhoDaBusca] = useState('');
  const [confirmacao, setConfirmacao] = useState<Confirmacao | null>(null);
  const [executando, setExecutando] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);

  const lista = useAsync(
    () =>
      api.getAdminUsers({
        status: filtros.status || undefined,
        role: filtros.role || undefined,
        verificado: filtros.verificado === '' ? undefined : filtros.verificado === 'sim',
        busca: filtros.busca || undefined,
        page: pagina,
        size: POR_PAGINA,
      }),
    [filtros.status, filtros.role, filtros.verificado, filtros.busca, pagina],
  );

  /**
   * O que mudou desde que a página carregou.
   *
   * A ação devolve a conta já atualizada, e é ela que a linha passa a mostrar — recarregar a lista
   * inteira faria a linha recém-bloqueada sumir na hora, se houvesse filtro por estado, e quem
   * clicou não veria o que aconteceu. Some quando a consulta muda, que é quando os dados são
   * outros.
   */
  const [ajustes, setAjustes] = useState<Record<number, AdminUserView>>({});
  useEffect(() => {
    setAjustes({});
  }, [filtros.status, filtros.role, filtros.verificado, filtros.busca, pagina]);

  /** Trocar filtro ou busca sempre volta para a primeira página: a página 4 do filtro velho não existe. */
  function aplicar(mudanca: Partial<Filtros>) {
    setFiltros((atual) => ({ ...atual, ...mudanca }));
    setPagina(0);
    setFalha(null);
  }

  async function executar(motivo: string) {
    if (!confirmacao) return;
    const { acao, conta } = confirmacao;
    const id = conta.id;
    if (id == null) return;

    setExecutando(true);
    setFalha(null);
    try {
      const atualizada =
        acao === 'bloquear'
          ? await api.setAdminUserStatus(id, 'RECUSADO', motivo.trim() || undefined)
          : acao === 'desbloquear'
            ? await api.setAdminUserStatus(id, 'APROVADO', motivo.trim() || undefined)
            : await api.setAdminUserRole(id, acao === 'promover' ? 'ADMIN' : 'USUARIO');
      setAjustes((atuais) => ({ ...atuais, [id]: atualizada }));
    } catch (cause) {
      // A linha continua como estava: em conflito nada mudou no servidor, e mostrar outra coisa
      // aqui seria mentira até o próximo carregamento.
      setFalha(mensagemDaFalha(cause));
    } finally {
      setExecutando(false);
      setConfirmacao(null);
    }
  }

  const contas = (lista.data?.items ?? []).map((conta) =>
    conta.id != null ? (ajustes[conta.id] ?? conta) : conta,
  );
  const total = lista.data?.total ?? 0;
  const totalPaginas = lista.data?.totalPages ?? 0;
  const carregando = lista.loading && !lista.data;

  return (
    <div className="stack">
      <section className="card">
        <header className="card__header">
          <h2>Usuários</h2>
          {lista.data && <span className="badge">{total}</span>}
        </header>
        <p className="hint">
          Todas as contas do app, da mais recente para a mais antiga. Bloquear e promover são ações
          sobre uma pessoa — cada uma pede confirmação.
        </p>

        <form
          className="admin-users__filtros"
          role="search"
          onSubmit={(event) => {
            event.preventDefault();
            aplicar({ busca: rascunhoDaBusca.trim() });
          }}
        >
          <label className="feedback-form__field admin-users__filtro--busca">
            <span className="feedback-form__label">Buscar</span>
            <input
              className="feedback-form__control"
              type="search"
              value={rascunhoDaBusca}
              onChange={(event) => setRascunhoDaBusca(event.target.value)}
              placeholder="trecho de e-mail ou nome"
              maxLength={120}
            />
          </label>

          <label className="feedback-form__field">
            <span className="feedback-form__label">Estado</span>
            <select
              className="feedback-form__control"
              value={filtros.status}
              onChange={(event) => aplicar({ status: event.target.value as AccessStatus | '' })}
            >
              <option value="">Todos</option>
              <option value="APROVADO">Ativas</option>
              <option value="RECUSADO">Bloqueadas</option>
            </select>
          </label>

          <label className="feedback-form__field">
            <span className="feedback-form__label">Papel</span>
            <select
              className="feedback-form__control"
              value={filtros.role}
              onChange={(event) => aplicar({ role: event.target.value as Role | '' })}
            >
              <option value="">Todos</option>
              <option value="ADMIN">Administração</option>
              <option value="USUARIO">Comum</option>
            </select>
          </label>

          <label className="feedback-form__field">
            <span className="feedback-form__label">E-mail</span>
            <select
              className="feedback-form__control"
              value={filtros.verificado}
              onChange={(event) =>
                aplicar({ verificado: event.target.value as Filtros['verificado'] })
              }
            >
              <option value="">Todos</option>
              <option value="sim">Confirmado</option>
              <option value="nao">Não confirmado</option>
            </select>
          </label>

          <div className="admin-users__filtro--acoes">
            <button type="submit">Buscar</button>
          </div>
        </form>

        {falha && (
          <p className="error" role="alert">
            {falha}
          </p>
        )}

        {carregando && <p className="empty">Carregando…</p>}

        {/* Falha de rede e lista vazia são coisas diferentes, e a tela não pode deixar as duas com
            a mesma cara: uma pede tentar de novo, a outra pede mudar o filtro. */}
        {lista.error && (
          <div className="admin-users__falha" role="alert">
            <p className="error">Não foi possível carregar as contas: {lista.error.message}</p>
            <button type="button" onClick={lista.reload}>
              Tentar de novo
            </button>
          </div>
        )}

        {!lista.error && !carregando && contas.length === 0 && (
          <p className="empty">Nenhuma conta com esses filtros.</p>
        )}

        {contas.length > 0 && (
          <>
            <div className="admin-users__rolagem">
              <table className="admin-users__tabela">
                <thead>
                  <tr>
                    <th scope="col">Nome</th>
                    <th scope="col">E-mail</th>
                    <th scope="col">Entra por</th>
                    <th scope="col">E-mail confirmado</th>
                    <th scope="col">Papel</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Criada em</th>
                    <th scope="col">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {contas.map((conta) => (
                    <LinhaDaConta
                      key={conta.id}
                      conta={conta}
                      ehVoce={!!conta.email && conta.email === account.email}
                      ocupado={executando}
                      onAcao={(acao) => {
                        setFalha(null);
                        setConfirmacao({ acao, conta });
                      }}
                    />
                  ))}
                </tbody>
              </table>
            </div>

            <div className="admin-users__paginacao">
              <button
                type="button"
                onClick={() => setPagina((atual) => Math.max(0, atual - 1))}
                disabled={pagina === 0 || lista.loading}
              >
                Anterior
              </button>
              <span className="hint">
                Página {pagina + 1} de {Math.max(totalPaginas, 1)} · {total}{' '}
                {total === 1 ? 'conta' : 'contas'}
              </span>
              <button
                type="button"
                onClick={() => setPagina((atual) => atual + 1)}
                disabled={pagina + 1 >= totalPaginas || lista.loading}
              >
                Próxima
              </button>
            </div>
          </>
        )}
      </section>

      {confirmacao && (
        <DialogoDeConfirmacao
          key={`${confirmacao.acao}-${confirmacao.conta.id ?? 0}`}
          confirmacao={confirmacao}
          executando={executando}
          onCancelar={() => setConfirmacao(null)}
          onConfirmar={(motivo) => void executar(motivo)}
        />
      )}
    </div>
  );
}

function LinhaDaConta({
  conta,
  ehVoce,
  ocupado,
  onAcao,
}: {
  conta: AdminUserView;
  ehVoce: boolean;
  ocupado: boolean;
  onAcao: (acao: Acao) => void;
}) {
  const bloqueada = conta.accessStatus === 'RECUSADO';
  const administra = conta.role === 'ADMIN';
  const alvo = conta.email ?? conta.label ?? 'esta conta';

  return (
    <tr>
      <th scope="row" className="admin-users__nome">
        {conta.label ?? '—'}
        {ehVoce && <span className="admin-users__voce"> (você)</span>}
      </th>
      <td>{conta.email ?? '—'}</td>
      <td>{provedores(conta.providers)}</td>
      <td>
        <span
          className={`admin-users__chip${conta.emailVerified ? '' : ' admin-users__chip--aviso'}`}
        >
          {conta.emailVerified ? 'Confirmado' : 'Não confirmado'}
        </span>
      </td>
      <td>
        <span className={`admin-users__chip${administra ? ' admin-users__chip--admin' : ''}`}>
          {administra ? 'Administração' : 'Comum'}
        </span>
      </td>
      <td>
        <span className={`admin-users__chip${bloqueada ? ' admin-users__chip--bloqueada' : ''}`}>
          {bloqueada ? 'Bloqueada' : 'Ativa'}
        </span>
      </td>
      <td>{dataCurta(conta.createdAt)}</td>
      <td>
        <div className="admin-users__acoes">
          <button
            type="button"
            className={bloqueada ? undefined : 'danger'}
            aria-label={`${bloqueada ? 'Desbloquear' : 'Bloquear'} a conta ${alvo}`}
            onClick={() => onAcao(bloqueada ? 'desbloquear' : 'bloquear')}
            disabled={ocupado}
          >
            {bloqueada ? 'Desbloquear' : 'Bloquear'}
          </button>
          <button
            type="button"
            aria-label={`${administra ? 'Rebaixar' : 'Promover'} a conta ${alvo}`}
            onClick={() => onAcao(administra ? 'rebaixar' : 'promover')}
            disabled={ocupado}
          >
            {administra ? 'Rebaixar' : 'Promover'}
          </button>
        </div>
      </td>
    </tr>
  );
}

/**
 * Confirmação de ação sobre uma pessoa.
 *
 * Diálogo de verdade, e não `window.confirm`: o texto precisa dizer o e-mail de quem vai ser
 * afetado e o que acontece depois do clique — coisa que a caixa do navegador não formata, e que
 * teste nenhum consegue ler.
 */
function DialogoDeConfirmacao({
  confirmacao,
  executando,
  onCancelar,
  onConfirmar,
}: {
  confirmacao: Confirmacao;
  executando: boolean;
  onCancelar: () => void;
  onConfirmar: (motivo: string) => void;
}) {
  const [motivo, setMotivo] = useState('');
  const texto = TEXTO_DA_ACAO[confirmacao.acao];
  const pedeMotivo = confirmacao.acao === 'bloquear' || confirmacao.acao === 'desbloquear';
  const destrutiva = confirmacao.acao === 'bloquear';

  // Esc cancela: a saída de uma confirmação tem que ser mais fácil que a entrada.
  useEffect(() => {
    function aoTeclar(event: KeyboardEvent) {
      if (event.key === 'Escape') onCancelar();
    }
    document.addEventListener('keydown', aoTeclar);
    return () => document.removeEventListener('keydown', aoTeclar);
  }, [onCancelar]);

  return (
    <div className="dialogo">
      <div
        className="dialogo__card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dialogo-titulo"
      >
        <h3 id="dialogo-titulo">{texto.titulo}</h3>
        {/* O e-mail escrito por extenso é o ponto do diálogo: é o que separa "bloquear uma linha
            da tabela" de "bloquear esta pessoa". */}
        <p className="dialogo__alvo">
          <strong>{confirmacao.conta.email ?? 'conta sem e-mail'}</strong>
          {confirmacao.conta.label && (
            <span className="dialogo__rotulo"> · {confirmacao.conta.label}</span>
          )}
        </p>
        <p className="dialogo__efeito">{texto.efeito}</p>

        {pedeMotivo && (
          <label className="feedback-form__field">
            <span className="feedback-form__label">Motivo (opcional, fica registrado)</span>
            <input
              className="feedback-form__control"
              type="text"
              value={motivo}
              onChange={(event) => setMotivo(event.target.value)}
              maxLength={280}
            />
          </label>
        )}

        <div className="dialogo__acoes">
          <button
            type="button"
            className={destrutiva ? 'danger' : undefined}
            onClick={() => onConfirmar(motivo)}
            disabled={executando}
          >
            {executando ? 'Aplicando…' : texto.confirmar}
          </button>
          <button type="button" onClick={onCancelar} disabled={executando}>
            Cancelar
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * Provedores vinculados, com o nome que a pessoa reconhece.
 *
 * Mesmo problema da tela da conta (#87): `password` é id interno, e uma coluna de tabela cheia de
 * id de banco não informa nada. Id desconhecido aparece como veio — ainda diz mais que um traço.
 */
function provedores(ids: readonly string[] | undefined): string {
  if (!ids || ids.length === 0) return '—';
  const conhecidos: Record<string, string> = {
    password: 'e-mail e senha',
    google: 'Google',
    facebook: 'Facebook',
    email: 'link por e-mail',
  };
  return ids.map((id) => conhecidos[id] ?? id).join(' · ');
}

/** Data sem hora: o que interessa numa lista é quando a conta apareceu, não a que horas. */
function dataCurta(iso: string | undefined): string {
  if (!iso) return '—';
  const data = new Date(iso);
  return Number.isNaN(data.getTime()) ? '—' : data.toLocaleDateString('pt-BR');
}
