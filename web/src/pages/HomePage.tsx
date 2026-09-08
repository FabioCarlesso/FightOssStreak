import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';
import { StreakCard } from '../components/StreakCard.tsx';
import { monthRange, todayIso } from '../content/diario.ts';

/**
 * Tela inicial: streak e, principalmente, a agenda de "o que drillar hoje".
 *
 * A agenda vem antes de tudo de propósito — é o que o produto se propõe a responder. Um app que
 * abre mostrando só o streak seria exatamente o caso de falha descrito em docs/05.
 *
 * O diário entra **abaixo** dela, e isso é decisão e não layout (D56c): a agenda é o único
 * elemento que o caderno não faz, e rebaixá-la seria virar o BJJ Notes com quiz junto. O contador
 * de treinos no mês é número **descritivo, sem meta** — quem tem meta é a agenda.
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
  const mes = useMemo(() => monthRange(todayIso()), []);
  const diario = useAsync(() => api.getDiary({ ...mes, limite: 1 }), [mes.de, mes.ate]);

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

        {agenda.data && agenda.data.due?.length === 0 && (
          <p className="empty">
            Nada vencido hoje. Conclua nós na <Link to="/arvore">árvore</Link> para começar a
            alimentar a agenda de revisão.
          </p>
        )}

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

function formatOverdue(days: number): string {
  if (days <= 0) return 'para hoje';
  if (days === 1) return '1 dia atrasado';
  return `${days} dias atrasado`;
}

function overdueClass(days: number): string {
  return days > 0 ? 'pill pill--late' : 'pill';
}
