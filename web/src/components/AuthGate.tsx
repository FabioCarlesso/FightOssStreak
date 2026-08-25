import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { ApiError, api } from '../api/client.ts';
import { AccountContext } from '../state/account.ts';
import { clearDemoSession, hadDemoSession, useStartDemo } from '../state/demoAccount.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * Portão de autenticação — o primeiro a decidir, antes do aviso de responsabilidade.
 *
 * Desde a D47 ele tem **uma** decisão a tomar, e não três: há sessão, ou não há. O estado
 * intermediário que a D36 criou ("autenticado, mas ainda não liberado") deixou de existir junto com
 * a fila de aprovação — com cadastro aberto, aprovação não filtra ninguém, só atrasa. Sumiram com
 * ele a tela de solicitação registrada e a de recusa.
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
