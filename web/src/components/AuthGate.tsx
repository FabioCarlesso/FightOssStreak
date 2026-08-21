import type { AccountView } from '@fos/types';
import { type FormEvent, type ReactNode, useState } from 'react';
import { ApiError, api } from '../api/client.ts';
import { AccountContext } from '../state/account.ts';
import { clearDemoSession, hadDemoSession, useStartDemo } from '../state/demoAccount.ts';
import { useAsync } from '../state/useAsync.ts';
import { DeleteAccount } from './DeleteAccount.tsx';
import { SignOutButton } from './SignOutButton.tsx';

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
 *
 * Há um quarto caso desde a #62, e ele é uma leitura do 401, não um estado novo do servidor:
 * demonstração vencida responde **como sessão inexistente**, porque é isso que ela é. Quem sabe
 * que havia uma demonstração em curso é o navegador (`hadDemoSession`), e é só por isso que a tela
 * consegue explicar o que aconteceu em vez de mostrar "entrar" para quem estava no meio do app.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const session = useAsync(() => api.getAccount(), []);

  if (session.loading && !session.data) {
    return <p className="empty">Carregando…</p>;
  }

  if (session.error) {
    // Sem sessão é o caso comum, não uma falha: é assim que todo mundo chega na primeira vez.
    if (session.error instanceof ApiError && session.error.isUnauthenticated) {
      return hadDemoSession() ? <DemoEndedScreen onChange={session.reload} /> : <LoginScreen />;
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
 * Um botão por provedor habilitado, e a saída por e-mail para quem não tem nenhum.
 *
 * Os botões de provedor são âncoras, e não `fetch`: o fluxo OAuth é uma navegação de página inteira
 * para o provedor e de volta. Chamada assíncrona seria bloqueada e não levaria a lugar nenhum.
 *
 * Desde a #52 entrar por provedor dá acesso direto — quem chega por ali já teve a identidade
 * verificada por um terceiro. A aprovação do autor passou a valer para quem entra por e-mail.
 */
function LoginScreen() {
  const providers = useAsync(() => api.getAuthProviders(), []);
  const [porEmail, setPorEmail] = useState(false);
  const list = providers.data?.providers ?? [];
  const emailEnabled = providers.data?.emailEnabled ?? false;
  // O backend redireciona para cá quando o link do e-mail não vale mais. Sem isto a pessoa
  // voltaria para a tela de login sem entender por quê.
  const linkInvalido = new URLSearchParams(window.location.search).get('erro') === 'link_invalido';

  if (porEmail) {
    return <EmailScreen onBack={() => setPorEmail(false)} />;
  }

  return (
    <div className="gate">
      <div className="gate__card">
        <h2>Entrar no FightOssStreak</h2>
        <p>
          O app é de uso pessoal. Entrar por um provedor dá acesso direto — o app nunca vê sua
          senha.
        </p>

        {linkInvalido && (
          <p className="gate__error">
            Esse link de entrada não vale mais: ele expira em 15 minutos e só funciona uma vez. Peça
            um novo abaixo.
          </p>
        )}

        {providers.loading && <p className="empty">Carregando…</p>}

        {providers.error && (
          <>
            <p className="gate__error">{providers.error.message}</p>
            <button type="button" onClick={providers.reload}>
              Tentar de novo
            </button>
          </>
        )}

        {!providers.loading && !providers.error && list.length === 0 && !emailEnabled && (
          <p className="gate__error">
            Nenhuma forma de entrada está configurada neste ambiente. Defina as credenciais
            descritas no README para habilitar o login.
          </p>
        )}

        <div className="login__providers">
          {list.map((provider) => (
            <a key={provider.id} className="login__provider" href={provider.authorizationUrl}>
              Entrar com {provider.label}
            </a>
          ))}
        </div>

        {emailEnabled && (
          <p className="login__alternativa">
            <button type="button" className="linklike" onClick={() => setPorEmail(true)}>
              Não tenho nenhuma dessas contas
            </button>
          </p>
        )}
      </div>
    </div>
  );
}

/**
 * Entrada por e-mail: pedir acesso, ou pedir o link de quem já foi liberado.
 *
 * As duas ações mostram a mesma classe de resposta de propósito. O backend responde igual para
 * endereço inexistente, pendente, recusado e aprovado — dizer mais transformaria a tela em consulta
 * de quem tem conta no app.
 */
function EmailScreen({ onBack }: { onBack: () => void }) {
  const [email, setEmail] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [pronto, setPronto] = useState<'pedido' | 'link' | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  async function enviar(event: FormEvent, acao: 'pedido' | 'link') {
    event.preventDefault();
    setEnviando(true);
    setFailure(null);
    try {
      await (acao === 'pedido' ? api.requestEmailAccess(email) : api.requestEmailLogin(email));
      setPronto(acao);
    } catch (cause) {
      setFailure(cause instanceof Error ? cause.message : String(cause));
    } finally {
      setEnviando(false);
    }
  }

  if (pronto === 'pedido') {
    return (
      <div className="gate">
        <div className="gate__card">
          <h2>Pedido registrado</h2>
          <p>
            Seu pedido de acesso foi registrado para <strong>{email}</strong>. Falta a liberação do
            autor do app — quando ela sair, você recebe um link de entrada nesse endereço.
          </p>
          <p>O autor é avisado automaticamente — não precisa cobrar por fora.</p>
          <div className="gate__actions">
            <button type="button" onClick={onBack}>
              Voltar
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (pronto === 'link') {
    return (
      <div className="gate">
        <div className="gate__card">
          <h2>Confira seu e-mail</h2>
          <p>
            Se existe uma conta liberada para <strong>{email}</strong>, o link de entrada acabou de
            sair. Ele vale por 15 minutos e só funciona uma vez.
          </p>
          <div className="gate__actions">
            <button type="button" onClick={onBack}>
              Voltar
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="gate">
      <div className="gate__card">
        <h2>Entrar por e-mail</h2>
        <p>
          Sem provedor, o acesso é <strong>sob aprovação do autor</strong>. Peça acesso e, quando
          for liberado, você entra por um link enviado para o seu e-mail.
        </p>

        <form className="login__email" onSubmit={(event) => void enviar(event, 'pedido')}>
          <label htmlFor="email-acesso">Seu e-mail</label>
          <input
            id="email-acesso"
            type="email"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="voce@exemplo.com"
          />
          {failure && <p className="gate__error">{failure}</p>}
          <div className="gate__actions">
            <button type="submit" disabled={enviando || !email}>
              {enviando ? 'Enviando…' : 'Pedir acesso'}
            </button>
            <button
              type="button"
              disabled={enviando || !email}
              onClick={(event) => void enviar(event, 'link')}
            >
              Já tenho acesso, quero o link
            </button>
          </div>
        </form>

        <p className="login__alternativa">
          <button type="button" className="linklike" onClick={onBack}>
            Voltar
          </button>
        </p>
      </div>
    </div>
  );
}

/**
 * A demonstração acabou.
 *
 * Duas saídas, e as duas são honestas: começar outra (que é outra conta, do zero — o que foi feito
 * na anterior não volta, porque foi apagado) ou entrar de verdade. A segunda limpa a marca antes
 * de recarregar, senão o 401 seguinte cairia de novo nesta tela.
 */
function DemoEndedScreen({ onChange }: { onChange: () => void }) {
  const demo = useStartDemo(() => onChange());

  function entrarDeVerdade() {
    clearDemoSession();
    onChange();
  }

  return (
    <div className="gate">
      <div className="gate__card">
        <h2>A demonstração terminou</h2>
        {/* "Encerrada", e não "apagada": a varredura é preguiçosa e roda quando alguém abre a
            próxima demonstração, então neste instante a conta ainda pode estar no banco. A frase
            precisa ser verdadeira nos dois momentos. */}
        <p>
          Ela dura duas horas e some sozinha — a conta temporária foi encerrada e é apagada em
          seguida, com tudo que foi feito nela. Nada disso era seu, e é por isso que dá para
          recomeçar sem perder nada.
        </p>
        {demo.failure && <p className="gate__error">{demo.failure}</p>}
        <div className="gate__actions">
          <button type="button" onClick={demo.start} disabled={demo.starting}>
            {demo.starting ? 'Abrindo…' : 'Começar outra demonstração'}
          </button>
          <button type="button" onClick={entrarDeVerdade}>
            Entrar na minha conta
          </button>
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
        <p>O autor é avisado automaticamente. Quando o acesso sair, é só entrar de novo.</p>
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
