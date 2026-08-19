import type { DrillEntry } from '@fos/types';
import { recallLabel } from '../content/recall.ts';

/**
 * Histórico de treinos do nó, com as anotações escritas em cada um.
 *
 * O dado já vinha na resposta de `GET /api/nodes/{code}` desde sempre — e era descartado pela tela.
 * O efeito era a anotação do drill funcionar como escrita no vazio: o placeholder pede "o que o
 * professor corrigiu" e o texto nunca mais aparecia.
 *
 * Registro sem anotação continua aparecendo, só que como marca de treino: é ele que conta a
 * frequência com que a técnica foi visitada, e some-lo deixaria buracos no histórico.
 */
export function DrillHistory({ entries }: { entries?: DrillEntry[] }) {
  if (!entries || entries.length === 0) {
    return (
      <p className="empty">
        Nenhum treino registrado neste nó ainda. O que você anotar ao registrar um drill aparece
        aqui.
      </p>
    );
  }

  return (
    <ol className="drill-history">
      {entries.map((entry, index) => (
        <li key={`${entry.drilledOn}-${index}`} className="drill-history__item">
          <p className="drill-history__meta">
            <time dateTime={entry.drilledOn}>{formatDate(entry.drilledOn)}</time>
            <span className="pill">{recallLabel(entry.recall)}</span>
          </p>
          {entry.note && <p className="drill-history__note">{entry.note}</p>}
        </li>
      ))}
    </ol>
  );
}

/**
 * `2026-08-16` vira `16/08/2026` sem passar por `new Date`, que interpretaria a data como UTC e
 * mostraria o dia anterior para quem está a oeste de Greenwich — que é o caso aqui.
 */
function formatDate(iso: string | undefined): string {
  if (!iso) return '';
  const [year, month, day] = iso.split('-');
  return day && month && year ? `${day}/${month}/${year}` : iso;
}
