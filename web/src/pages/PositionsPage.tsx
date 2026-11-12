import { Link } from 'react-router-dom';
import { groupPositionsByFamily, nodesFromPosition } from '@fos/domain';
import type { CurriculumPosition, NodeSummary } from '@fos/types';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';
import { useDemoMode } from '../state/demoMode.ts';
import { NodeRow } from './TreePage.tsx';

/**
 * Mapa de posições (#194, D76): as posições do jiu-jitsu, por família, com as técnicas do
 * currículo que partem de cada uma.
 *
 * É outro corte da mesma árvore, e não um segundo currículo: posições e status vêm do mesmo
 * `GET /api/curriculum/tree`, e cada linha de técnica é a da árvore. O texto de cada posição diz
 * **como reconhecê-la** — identificar sim, ensinar não (D1).
 */
export function PositionsPage() {
  const tree = useAsync(() => api.getTree(), []);
  const demo = useDemoMode();

  if (tree.loading && !tree.data) return <p className="empty">Carregando posições…</p>;
  if (tree.error && !tree.data) return <p className="error">{tree.error.message}</p>;
  if (!tree.data) return null;

  const nodes: NodeSummary[] = (tree.data.modules ?? []).flatMap((module) => module.nodes ?? []);
  const grupos = groupPositionsByFamily(tree.data.positions ?? []);

  return (
    <div className="stack">
      <section className="card">
        <header className="card__header">
          <h2>Posições</h2>
          <Link to="/arvore">Voltar à árvore</Link>
        </header>
        <p className="tree__summary">
          Cada posição com o que a distingue e as técnicas do currículo que partem dela. Para saber
          como chegar e o que fazer dali, o lugar é a aula.
        </p>
      </section>

      {grupos.map((grupo) => (
        <section className="card" key={grupo.family}>
          <h2>{grupo.label}</h2>
          <ul className="position-list">
            {grupo.positions.map((position) => (
              <PositionItem
                key={position.code}
                position={position}
                nodes={nodesFromPosition(nodes, position.code ?? '')}
                demo={demo.enabled}
              />
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

function PositionItem({
  position,
  nodes,
  demo,
}: {
  position: CurriculumPosition;
  nodes: NodeSummary[];
  demo: boolean;
}) {
  const aliases = position.aliases ?? [];
  return (
    <li className="position" id={position.code}>
      <h3 className="position__name">{position.name}</h3>
      {aliases.length > 0 && (
        <p className="position__aliases">Também chamada de {aliases.join(', ')}</p>
      )}
      <p className="position__identify">{position.identify}</p>
      {nodes.length === 0 ? (
        <p className="empty">Ainda não há técnica no currículo que parta desta posição.</p>
      ) : (
        <ul className="node-list">
          {nodes.map((node) => (
            <NodeRow key={node.code} node={node} demo={demo} />
          ))}
        </ul>
      )}
    </li>
  );
}
