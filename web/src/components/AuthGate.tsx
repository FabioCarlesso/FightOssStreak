import type { AccountView } from '@fos/types';
import { type ReactNode } from 'react';
import { ApiError, api } from '../api/client.ts';
import { AccountContext } from '../state/account.ts';
import { useAsync } from '../state/useAsync.ts';
import { DeleteAccount } from './DeleteAccount.tsx';

/**
 * Portão de autenticação e de aprovação — o primeiro a decidir, antes do aviso de responsabilidade.
 *
 * São três respostas possíveis antes do app: **sem sessão** leva à tela de login; **conta na fila**
 * leva à tela de solicitação registrada; **conta recusada** leva à tela de recusa. Só conta
 * aprovada segue para o `DisclaimerGate` — pedir aceite de responsabilidade a quem talvez nunca
 * entre não faria sentido, e o aceite é por conta (docs/06).
 *
 * O 403 com motivo de acesso não passa por aqui: `GET /api/me` responde 200 para conta pendente ou
 * recusada, justamente para esta tela ter o que mostrar. O 403 é o que o resto da API devolve.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const session = useAsync(() => api.getAccount(), []);

  if (session.loading && !session.data) {
    return <p className="empty">Carregando…</p>;
  }

  if (session.error) {
    // Sem sessão é o caso comum, não uma falha: é assim que todo mundo chega na primeira vez.
    if (session.error instanceof ApiError && session.error.isUnauthenticated) {
      return <LoginScreen />;
    }
    return (
      <div className="gate">
        <div className="gate__card">
          <h2>Não foi possível falar com a API</h2>
          <p className="gate__error">{session.error.message}</p>
          <p>
            O backend está rodando? <code>npm run dev:backend</code>
          </p>
          <button type="button" onClick={session.reload}>
            Tentar de novo
          </button>
        </div>
      </div>
    );
  }

  const account = session.data;
  if (!account) {
    return <p className="empty">Carregando…</p>;
  }

  if (account.accessStatus === 'PENDENTE') {
    return <PendingScreen account={account} onChange={session.reload} />;
  }

  if (account.accessStatus === 'RECUSADO') {
    return <DeniedScreen onChange={session.reload} />;
  }

  return (
    <AccountContext.Provider value={{ account, reload: session.reload }}>
      {children}
    </AccountContext.Provider>
  );
}

/**
 * Um botão por provedor habilitado.
 *
 * Os botões são âncoras, e não `fetch`: o fluxo OAuth é uma navegação de página inteira para o
 * provedor e de volta. Chamada assíncrona seria bloqueada e não levaria a lugar nenhum.
 */
function LoginScreen() {
  const providers = useAsync(() => api.getAuthProviders(), []);
  const list = providers.data?.providers ?? [];

  return (
    <div className="gate">
      <div className="gate__card">
        <h2>Entrar no FightOssStreak</h2>
        <p>
          O app é de uso pessoal e o <strong>acesso é sob aprovação</strong>. Entre por um provedor
          para registrar sua solicitação — o app nunca vê sua senha.
        </p>

        {providers.loading && <p className="empty">Carregando…</p>}

        {providers.error && (
          <>
            <p className="gate__error">{providers.error.message}</p>
            <button type="button" onClick={providers.reload}>
              Tentar de novo
            </button>
          </>
        )}

        {!providers.loading && !providers.error && list.length === 0 && (
          <p className="gate__error">
            Nenhum provedor de login está configurado neste ambiente. Defina as credenciais
            descritas no README para habilitar a entrada.
          </p>
        )}

        <div className="login__providers">
          {list.map((provider) => (
            <a key={provider.id} className="login__provider" href={provider.authorizationUrl}>
              Entrar com {provider.label}
            </a>
          ))}
        </div>
      </div>
    </div>
  );
}

function PendingScreen({ account, onChange }: { account: AccountView; onChange: () => void }) {
  return (
    <div className="gate">
      <div className="gate__card">
        <h2>Solicitação registrada</h2>
        <p>
          Você entrou como <strong>{account.displayName}</strong>
          {account.email ? ` (${account.email})` : ''}
          {account.provider ? `, pelo ${account.provider}` : ''}. Falta a liberação do autor do app
          — até lá não há árvore, progresso nem agenda.
        </p>
        <p>
          Avise pelo mesmo canal por onde você chegou até aqui. Quando o acesso sair, é só entrar de
          novo.
        </p>
        <div className="gate__actions">
          <button type="button" onClick={onChange}>
            Verificar de novo
          </button>
          <SignOutButton onSignedOut={onChange} />
        </div>
        <div className="gate__danger">
          <DeleteAccount onDeleted={onChange} label="Cancelar e apagar meus dados" />
        </div>
      </div>
    </div>
  );
}

function DeniedScreen({ onChange }: { onChange: () => void }) {
  return (
    <div className="gate">
      <div className="gate__card">
        <h2>Acesso não liberado</h2>
        {/* Sem botão de pedir de novo: fila reciclada não é decisão nova, é a mesma recusa. */}
        <p>
          Sua solicitação de acesso foi recusada. Se isso parece engano, fale com o autor do app.
        </p>
        <div className="gate__actions">
          <SignOutButton onSignedOut={onChange} />
        </div>
        <div className="gate__danger">
          <DeleteAccount onDeleted={onChange} label="Apagar meus dados" />
        </div>
      </div>
    </div>
  );
}

/** Sair derruba a sessão no servidor; a releitura de `/api/me` é que leva de volta ao login. */
export function SignOutButton({ onSignedOut }: { onSignedOut: () => void }) {
  return (
    <button
      type="button"
      onClick={() => {
        void api.logout().finally(onSignedOut);
      }}
    >
      Sair
    </button>
  );
}
