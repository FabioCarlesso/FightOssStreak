import { useId, useState } from 'react';
import { groupPositionsByFamily, searchTechniques } from '@fos/domain';
import type { NodeSummary, TreeView } from '@fos/types';

/** Teto da lista de resultados: é escolha rápida, não catálogo. */
const MAX_RESULTS = 8;

/**
 * Escolher uma técnica do currículo para vincular ao treino (#194, D76).
 *
 * Busca por título, código e apelido — "kimura", "side control", "M3.7" —, com filtro por posição
 * de onde a técnica parte. A regra de busca é a de `@fos/domain`, para o app usar a mesma. Sem
 * texto e sem posição a lista fica vazia de propósito: listar todos os nós é o atrito que isto
 * substitui.
 */
export function TechniquePicker({
  tree,
  onPick,
  exclude = [],
}: {
  tree: TreeView | null | undefined;
  onPick: (node: NodeSummary) => void;
  /** Códigos que já estão vinculados ou marcados — não aparecem como opção. */
  exclude?: readonly string[];
}) {
  const id = useId();
  const [query, setQuery] = useState('');
  const [position, setPosition] = useState('');

  if (!tree) return <p className="empty">Carregando técnicas…</p>;

  const nodes = (tree.modules ?? []).flatMap((module) => module.nodes ?? []);
  const positions = tree.positions ?? [];
  const resultados = searchTechniques(nodes, { query, position }, positions).filter(
    (node) => !exclude.includes(node.code ?? ''),
  );
  const buscando = query.trim() !== '' || position !== '';

  return (
    <div className="technique-picker">
      <div className="technique-picker__filters">
        <label className="session-form__field">
          <span>Buscar técnica</span>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Nome, apelido ou código: kimura, side control…"
            aria-describedby={`${id}-dica`}
          />
        </label>
        <label className="session-form__field">
          <span>Posição</span>
          <select value={position} onChange={(event) => setPosition(event.target.value)}>
            <option value="">Todas</option>
            {groupPositionsByFamily(positions).map((grupo) => (
              <optgroup key={grupo.family} label={grupo.label}>
                {grupo.positions.map((item) => (
                  <option key={item.code} value={item.code}>
                    {item.name}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
        </label>
      </div>

      {!buscando && (
        <p className="hint" id={`${id}-dica`}>
          Digite o nome da técnica ou escolha a posição em que vocês treinaram.
        </p>
      )}
      {buscando && resultados.length === 0 && (
        <p className="empty">Nenhuma técnica do currículo com esse nome ou nessa posição.</p>
      )}

      {resultados.length > 0 && (
        <ul className="technique-picker__results">
          {resultados.slice(0, MAX_RESULTS).map((node) => (
            <li key={node.code}>
              <button
                type="button"
                className="technique-picker__option"
                onClick={() => {
                  onPick(node);
                  setQuery('');
                }}
              >
                <span className="due-list__code">{node.code}</span> {node.title}
              </button>
            </li>
          ))}
        </ul>
      )}
      {resultados.length > MAX_RESULTS && (
        <p className="hint">
          Mais {resultados.length - MAX_RESULTS} técnicas: refine a busca ou a posição.
        </p>
      )}
    </div>
  );
}
