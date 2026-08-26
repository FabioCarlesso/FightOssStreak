import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { ApiError, api } from '../api/client.ts';
import { DeleteAccount } from './DeleteAccount.tsx';
import { SignOutButton } from './SignOutButton.tsx';
import { AccountContext } from '../state/account.ts';
import { subscribeAccessDenied } from '../state/accessDenied.ts';
import { clearDemoSession, hadDemoSession, useStartDemo } from '../state/demoAccount.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * Portão de autenticação — o primeiro a decidir, antes do aviso de responsabilidade.
 *
 * Desde a D47 ele decide se há sessão, e não mais em que ponto de uma fila a pessoa está: o estado
 * intermediário que a D36 criou ("autenticado, mas ainda não liberado") deixou de existir junto com
 * a fila de aprovação — com cadastro aberto, aprovação não filtra ninguém, só atrasa. Sumiu com ele
 * a tela de solicitação registrada.
 *
 * O que voltou com a #90 é outra coisa, apesar de reusar o mesmo `RECUSADO`: **bloqueio reativo**,
 * decidido por quem administra depois de a conta existir. Não é fila; é interrupção. Daí a segunda
 * decisão deste portão, e a tela própria lá embaixo.
 *
 * Sem sessão, quem responde é `/entrar`, e o redirecionamento é o ponto: as telas de fronteira são
 * rotas públicas de verdade, porque o link de confirmação que chega por e-mail precisa voltar para
 * uma URL do app.
 *
 * Há um segundo caso desde a #62, e ele é uma leitura do 401, não um estado novo do servidor:
 * demonstração vencida responde **como sessão inexistente**, porque é isso que ela é. Quem sabe que
 * havia uma demonstração em curso é o navegador (`hadDemoSession`), e é só por isso que a tela
 * consegue explicar o que aconteceu em vez de mandar para o login quem estava no meio do app.
 */
export function AuthGate({ children }: { children: ReactNode }) {
  const session = useAsync(() => api.getAccount(), []);

  // Bloqueio acontece no meio do uso: quando alguma chamada volta `acesso_recusado`, o portão
  // reconsulta quem é a conta e cai na tela de bloqueio. Sem isto, a aba aberta mostraria o erro
  // de uma tela qualquer, e a única saída seria recarregar a página por conta própria.
  const { reload } = session;
  useEffect(() => subscribeAccessDenied(reload), [reload]);

  if (session.loading && !session.data) {
    return <p className="empty">Carregando…</p>;
  }

  if (session.error) {
    // Sem sessão é o caso comum, não uma falha: é assim que todo mundo chega na primeira vez.
    if (session.error instanceof ApiError && session.error.isUnauthenticated) {
      return hadDemoSession() ? (
        <DemoEndedScreen onChange={session.reload} />
      ) : (
        <Navigate to="/entrar" replace />
      );
    }
    // Conta bloqueada (#90): `/api/me` fica fora do portão do backend e responde 200 com
    // `RECUSADO`, então o caminho normal é o de baixo. Este aqui é a rede de segurança para
    // qualquer chamada que volte 403 — e ele existe para **não** mandar ninguém ao login: a
    // pessoa acabou de entrar, e redirecionar produziria um looping em vez de uma explicação.
    if (session.error instanceof ApiError && session.error.isAccessDenied) {
      return <BlockedAccountScreen onChange={session.reload} />;
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

  // Sessão viva, acesso bloqueado (#90). É por aqui que a aba de quem foi bloqueado cai na tela
  // certa: `/api/me` continua respondendo 200, e o estado vem no corpo.
  if (account.accessStatus === 'RECUSADO') {
    return <BlockedAccountScreen onChange={session.reload} />;
  }

  return (
    <AccountContext.Provider value={{ account, reload: session.reload }}>
      {children}
    </AccountContext.Provider>
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
            Criar conta ou entrar
          </button>
        </div>
      </div>
    </div>
  );
}

/**
 * A conta está bloqueada (#90, #91).
 *
 * A tela de recusa saiu do app junto com a fila de aprovação (D48), e volta com outro sentido: não
 * é mais "espere sua vez", é "alguém interrompeu este acesso". O texto explica o que houve, diz que
 * nada foi apagado e para. Sermão não devolve o acesso a ninguém, e quem lê pode ter sido bloqueado
 * por engano.
 *
 * As duas saídas são honestas e as duas continuam funcionando com a conta bloqueada: sair da sessão
 * e excluir a própria conta. A segunda é a que importa — `DELETE /api/me` fica de fora do portão de
 * propósito, porque bloquear não pode virar sequestro de dado pessoal.
 */
function BlockedAccountScreen({ onChange }: { onChange: () => void }) {
  return (
    <div className="gate">
      <div className="gate__card">
        <h2>Seu acesso está bloqueado</h2>
        <p>
          Uma conta de administração do FightOssStreak interrompeu o acesso desta conta. Enquanto
          isso valer, o app não abre — mas <strong>nada foi apagado</strong>: progresso, streak,
          agenda de revisão e anotações continuam onde estavam, e um desbloqueio devolve tudo como
          era.
        </p>
        <p className="hint">
          Se isso parece engano, responda ao e-mail pelo qual você recebe as mensagens do app.
        </p>
        <div className="gate__actions">
          <SignOutButton onSignedOut={onChange} />
        </div>
        <div className="gate__danger">
          <p className="hint">
            Se preferir não esperar, a conta é sua e continua sendo: dá para excluí-la agora, com
            tudo que é dela.
          </p>
          <DeleteAccount onDeleted={onChange} />
        </div>
      </div>
    </div>
  );
}
