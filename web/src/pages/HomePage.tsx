import { nextNodeToResume, type ResumeTarget } from '@fos/domain';
import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';
import { useDemoMode } from '../state/demoMode.ts';
import { StreakCard } from '../components/StreakCard.tsx';
import { monthRange, todayIso } from '../content/diario.ts';

/**
 * Tela inicial: streak e, principalmente, a agenda de "o que drillar hoje".
 *
 * A agenda vem antes de tudo de propósito — é o que o produto se propõe a responder. Um app que
 * abre mostrando só o streak seria exatamente o caso de falha descrito em docs/produto/mvp-web.md.
 *
 * O diário entra **abaixo** dela, e isso é decisão e não layout (D56c): a agenda é o único
 * elemento que o caderno não faz, e rebaixá-la seria virar o BJJ Notes com quiz junto. O contador
 * de treinos no mês é número **descritivo, sem meta** — quem tem meta é a agenda.
 *
 * O atalho "Continuar" (#104) mora **dentro** do card da agenda, e não num card acima dele, pelo
 * mesmo motivo: com revisão vencida ele aponta para o primeiro da própria agenda, e só sem nada
 * vencido ele sugere o próximo nó da árvore — onde antes havia apenas "conclua nós na árvore".
 */
export function HomePage() {
  const streak = useAsync(() => api.getStreak(), []);
  // Depois do streak, e não em paralelo: `GET /api/streak` é quem materializa o dia perdoado em
  // `streak_freeze` (D55), e o histórico só lê aquela tabela. Em paralelo, o primeiro carregamento
  // depois de um dia perdido mostraria o cartão dizendo "um freeze cobriu quinta" com o heatmap
  // marcando quinta como falta — duas afirmações diferentes sobre o mesmo dia na mesma tela.
  const historico = useAsync(
    () => (streak.data ? api.getStreakHistory() : Promise.resolve(null)),
    [streak.data],
  );
  const agenda = useAsync(() => api.getReviewsToday(), []);
  // A árvore só decide o atalho quando não há nada vencido, mas é pedida junto: esperar a agenda
  // para só então buscá-la deixaria o "Continuar" aparecer em dois tempos.
  const tree = useAsync(() => api.getTree(), []);
  const mes = useMemo(() => monthRange(todayIso()), []);
  const diario = useAsync(() => api.getDiary({ ...mes, limite: 1 }), [mes.de, mes.ate]);
  const demo = useDemoMode();
  const continuar = agenda.data ? resumeFrom(agenda.data.due, tree.data?.modules) : undefined;

  return (
    <div className="stack">
      {streak.data && <StreakCard streak={streak.data} historico={historico.data} />}

      <section className="card">
        <header className="card__header">
          <h2>Revise hoje</h2>
          {agenda.data && <span className="badge">{agenda.data.dueCount ?? 0}</span>}
        </header>

        {agenda.loading && <p className="empty">Carregando agenda…</p>}
        {agenda.error && <p className="error">{agenda.error.message}</p>}

        {agenda.data && agenda.data.due?.length === 0 && <EmptyAgenda continuar={continuar} />}

        {(agenda.data?.due?.length ?? 0) > 0 && <ContinueLink continuar={continuar} />}

        <ul className="due-list">
          {agenda.data?.due?.map((item) => (
            <li key={item.nodeCode}>
              <Link to={`/no/${item.nodeCode}`} className="due-list__item">
                <span className="due-list__main">
                  <span className="due-list__code">{item.nodeCode}</span>
                  <span className="due-list__title">{item.title}</span>
                </span>
                <span className={overdueClass(item.daysOverdue ?? 0)}>
                  {formatOverdue(item.daysOverdue ?? 0)}
                </span>
              </Link>
            </li>
          ))}
        </ul>

        {/* A lista continua: quem quer ir direto a um nó vai. O botão é para percorrer a fila
            inteira (#47) — e some em demonstração, que não grava (D31). */}
        {(agenda.data?.dueCount ?? 0) > 0 && !demo.enabled && (
          <p className="review__cta">
            <Link to="/revisar" className="button-link">
              Revisar agora
            </Link>
          </p>
        )}
      </section>

      <section className="card">
        <header className="card__header">
          <h2>Diário</h2>
          <Link to="/diario/nova" className="diary__cta">
            Registrar treino
          </Link>
        </header>
        <p className="hint">
          {diario.data ? (
            <>
              <strong>{diario.data.sessionsInMonth ?? 0}</strong>{' '}
              {diario.data.sessionsInMonth === 1 ? 'treino registrado' : 'treinos registrados'}{' '}
              neste mês. <Link to="/diario">Ver o diário</Link>.
            </>
          ) : (
            <>
              Rola solta, físico ou aula sem nada do currículo também cabem aqui.{' '}
              <Link to="/diario">Ver o diário</Link>.
            </>
          )}
        </p>
      </section>
    </div>
  );
}

/**
 * `undefined` enquanto a árvore não chegou (ou falhou) e não há vencido: aí não se sabe ainda se é
 * "tudo em dia" ou "há nó novo", e afirmar qualquer um dos dois seria errar metade das vezes.
 */
function resumeFrom(
  due: Parameters<typeof nextNodeToResume>[0] | undefined,
  modules: Parameters<typeof nextNodeToResume>[1] | undefined,
): ResumeTarget | null | undefined {
  const fila = due ?? [];
  if (fila.length === 0 && modules === undefined) return undefined;
  return nextNodeToResume(fila, modules ?? []);
}

const RESUME_HINT: Record<ResumeTarget['reason'], string> = {
  REVISAO: 'a revisão mais atrasada',
  EM_ANDAMENTO: 'nó em andamento',
  NOVO: 'próximo nó liberado da árvore',
};

function ContinueLink({ continuar }: { continuar: ResumeTarget | null | undefined }) {
  if (!continuar) return null;
  return (
    <p className="resume">
      <Link to={`/no/${continuar.code}`} className="resume__link">
        Continuar: <strong>{continuar.title}</strong>
      </Link>{' '}
      <span className="hint">— {RESUME_HINT[continuar.reason]}</span>
    </p>
  );
}

function EmptyAgenda({ continuar }: { continuar: ResumeTarget | null | undefined }) {
  if (continuar === null) {
    return <p className="empty">Tudo em dia: nada vencido hoje e nenhum nó novo para começar.</p>;
  }
  if (continuar === undefined) {
    return (
      <p className="empty">
        Nada vencido hoje. Conclua nós na <Link to="/arvore">árvore</Link> para começar a alimentar
        a agenda de revisão.
      </p>
    );
  }
  return (
    <>
      <p className="empty">Nada vencido hoje.</p>
      <ContinueLink continuar={continuar} />
    </>
  );
}

function formatOverdue(days: number): string {
  if (days <= 0) return 'para hoje';
  if (days === 1) return '1 dia atrasado';
  return `${days} dias atrasado`;
}

function overdueClass(days: number): string {
  return days > 0 ? 'pill pill--late' : 'pill';
}
