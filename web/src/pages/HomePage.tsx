import { Link } from 'react-router-dom';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';
import { StreakCard } from '../components/StreakCard.tsx';

/**
 * Tela inicial: streak e, principalmente, a agenda de "o que drillar hoje".
 *
 * A agenda vem antes de tudo de propósito — é o que o produto se propõe a responder. Um app que
 * abre mostrando só o streak seria exatamente o caso de falha descrito em docs/05.
 */
export function HomePage() {
  const streak = useAsync(() => api.getStreak(), []);
  const agenda = useAsync(() => api.getReviewsToday(), []);

  return (
    <div className="stack">
      {streak.data && <StreakCard streak={streak.data} />}

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
