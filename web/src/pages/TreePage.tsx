import { Link } from 'react-router-dom';
import type { NodeSummary } from '@fos/types';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';

/** Árvore de currículo com estado de bloqueio já resolvido pelo backend. */
export function TreePage() {
  const tree = useAsync(() => api.getTree(), []);

  if (tree.loading && !tree.data) return <p className="empty">Carregando currículo…</p>;
  if (tree.error && !tree.data) return <p className="error">{tree.error.message}</p>;
  if (!tree.data) return null;

  const summary = tree.data.summary;

  return (
    <div className="stack">
      <section className="card">
        <h2>Progresso</h2>
        <p className="tree__summary">
          <strong>{summary?.completedNodes ?? 0}</strong> concluídos ·{' '}
          <strong>{summary?.availableNodes ?? 0}</strong> disponíveis ·{' '}
          <strong>{summary?.lockedNodes ?? 0}</strong> bloqueados · {summary?.totalNodes ?? 0} nós
          no total
        </p>
      </section>

      {tree.data.modules?.map((module) => (
        <section className="card" key={module.code}>
          <header className="card__header">
            <h2>
              <span className="module__code">{module.code}</span> {module.title}
            </h2>
          </header>
          <p className="module__summary">{module.summary}</p>

          <ul className="node-list">
            {module.nodes?.map((node) => (
              <NodeRow key={node.code} node={node} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function NodeRow({ node }: { node: NodeSummary }) {
  const locked = node.status === 'LOCKED';
  const body = (
    <>
      <span className="node-row__status" aria-hidden="true">
        {statusIcon(node.status)}
      </span>
      <span className="node-row__label">
        <span className="node-row__code">{node.code}</span>
        <span className="node-row__title">{node.title}</span>
      </span>
      <span className="node-row__tags">
        <span className={`belt belt--${(node.belt ?? 'BRANCA').toLowerCase()}`}>{node.belt}</span>
        {(node.quizQuestionCount ?? 0) > 0 && <span className="tag">quiz</span>}
        {node.hasVideo && <span className="tag">vídeo</span>}
      </span>
    </>
  );

  if (locked) {
    return (
      <li className="node-row node-row--locked">
        <div className="node-row__inner">{body}</div>
        <p className="node-row__hint">
          {node.unlockRule === 'ANY'
            ? `Conclua qualquer um: ${(node.prereqCodes ?? []).join(', ')}`
            : `Requer: ${(node.prereqCodes ?? []).join(', ')}`}
        </p>
      </li>
    );
  }

  return (
    <li className={`node-row node-row--${(node.status ?? 'AVAILABLE').toLowerCase()}`}>
      <Link className="node-row__inner" to={`/no/${node.code}`}>
        {body}
      </Link>
    </li>
  );
}

function statusIcon(status: NodeSummary['status']): string {
  switch (status) {
    case 'COMPLETED':
      return '✓';
    case 'IN_PROGRESS':
      return '◐';
    case 'LOCKED':
      return '🔒';
    default:
      return '○';
  }
}
