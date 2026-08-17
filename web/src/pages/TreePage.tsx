import { Link } from 'react-router-dom';
import type { NodeSummary } from '@fos/types';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';
import { useDemoMode } from '../state/demoMode.ts';

/** Árvore de currículo com estado de bloqueio já resolvido pelo backend. */
export function TreePage() {
  const tree = useAsync(() => api.getTree(), []);
  const demo = useDemoMode();

  if (tree.loading && !tree.data) return <p className="empty">Carregando currículo…</p>;
  if (tree.error && !tree.data) return <p className="error">{tree.error.message}</p>;
  if (!tree.data) return null;

  const summary = tree.data.summary;

  return (
    <div className="stack">
      <section className="card">
        <header className="card__header">
          <h2>Progresso</h2>
          <button
            type="button"
            className={demo.enabled ? 'demo-toggle demo-toggle--on' : 'demo-toggle'}
            aria-pressed={demo.enabled}
            onClick={() => demo.setEnabled(!demo.enabled)}
          >
            Demonstração: {demo.enabled ? 'ligada' : 'desligada'}
          </button>
        </header>
        {/*
         * Os contadores são os números reais e continuam assim com a demonstração ligada: são eles
         * a referência do que ainda está travado de verdade.
         */}
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
              <NodeRow key={node.code} node={node} demo={demo.enabled} />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function NodeRow({ node, demo }: { node: NodeSummary; demo: boolean }) {
  const lockedByProgress = node.status === 'LOCKED';
  // Em demonstração o nó bloqueado é apresentado como disponível — o bloqueio sempre foi só de
  // apresentação, o backend já entrega conceito, vídeo e quiz de qualquer nó. A dica de
  // pré-requisito continua nos dois modos, agora como informação e não como portão.
  const shownStatus = lockedByProgress && demo ? 'AVAILABLE' : node.status;
  const hint = lockedByProgress && (
    <p className="node-row__hint">
      {node.unlockRule === 'ANY'
        ? `Conclua qualquer um: ${(node.prereqCodes ?? []).join(', ')}`
        : `Requer: ${(node.prereqCodes ?? []).join(', ')}`}
    </p>
  );

  const body = (
    <>
      <span className="node-row__status" aria-hidden="true">
        {statusIcon(shownStatus)}
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

  if (shownStatus === 'LOCKED') {
    return (
      <li className="node-row node-row--locked">
        <div className="node-row__inner">{body}</div>
        {hint}
      </li>
    );
  }

  return (
    <li className={`node-row node-row--${(shownStatus ?? 'AVAILABLE').toLowerCase()}`}>
      <Link className="node-row__inner" to={`/no/${node.code}`}>
        {body}
      </Link>
      {hint}
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
