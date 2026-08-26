import { DeleteAccount } from '../components/DeleteAccount.tsx';
import { useAccount } from '../state/account.ts';

/**
 * A conta vista por ela mesma: por onde você entrou e como sair de vez.
 *
 * Tela separada, e não um menu no cabeçalho, porque a ação principal daqui é irreversível.
 *
 * Em conta de demonstração (#62) a tela é a mesma, com os nomes trocados: "apagar meus dados" ali
 * seria mentira, porque nada daquilo é de ninguém. A ação por trás é a mesma `DELETE /api/me`.
 */
export function AccountPage() {
  const { account, reload } = useAccount();
  const demo = account.demoExpiresAt != null;

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
          <dd>
            {demo
              ? 'nenhum — a demonstração não pede e-mail'
              : (account.email ?? 'não informado pelo provedor')}
          </dd>
          <dt>Entrou por</dt>
          <dd>{demo ? 'demonstração pública' : rotuloDoProvedor(account.provider)}</dd>
        </dl>
      </section>

      <section className="card">
        <header className="card__header">
          {/* Mesma chamada, nome honesto: em demonstração não há dado de ninguém para apagar. */}
          <h2>{demo ? 'Encerrar demonstração' : 'Excluir minha conta'}</h2>
        </header>
        <DeleteAccount
          onDeleted={reload}
          demo={demo}
          label={demo ? 'Encerrar demonstração' : undefined}
        />
      </section>
    </div>
  );
}

/**
 * O provedor com o nome que a pessoa reconhece.
 *
 * O que vem da API é o id interno — `password`, `google` —, e ele nunca foi feito para ser lido:
 * "Entrou por: password" é a frase que sobra quando um campo de banco vaza para a tela. Ids que
 * este mapa não conhecer caem neles mesmos, porque um id estranho ainda diz mais que um traço.
 *
 * `email` é a entrada por link da #52, desmontada na D48: ninguém entra mais por ali, mas as
 * contas de quem entrava continuam no banco até se cadastrarem com senha no mesmo endereço.
 */
function rotuloDoProvedor(provider: string | null | undefined): string {
  if (!provider) return '—';
  const conhecidos: Record<string, string> = {
    password: 'e-mail e senha',
    google: 'Google',
    facebook: 'Facebook',
    email: 'link por e-mail (forma antiga de entrar)',
  };
  return conhecidos[provider] ?? provider;
}
