import { DeleteAccount } from '../components/DeleteAccount.tsx';
import { useAccount } from '../state/account.ts';

/**
 * A conta vista por ela mesma: por onde você entrou e como sair de vez.
 *
 * Tela separada, e não um menu no cabeçalho, porque a ação principal daqui é irreversível.
 */
export function AccountPage() {
  const { account, reload } = useAccount();

  return (
    <div className="stack">
      <section className="card">
        <header className="card__header">
          <h2>Sua conta</h2>
        </header>
        <dl className="account">
          <dt>Nome</dt>
          <dd>{account.displayName}</dd>
          <dt>E-mail</dt>
          {/* O provedor pode não devolver e-mail — Facebook com conta criada por telefone, Apple
              com "esconder meu e-mail". Dizer isso é melhor que mostrar um campo vazio. */}
          <dd>{account.email ?? 'não informado pelo provedor'}</dd>
          <dt>Entrou por</dt>
          <dd>{account.provider ?? '—'}</dd>
        </dl>
      </section>

      <section className="card">
        <header className="card__header">
          <h2>Excluir minha conta</h2>
        </header>
        <DeleteAccount onDeleted={reload} />
      </section>
    </div>
  );
}
