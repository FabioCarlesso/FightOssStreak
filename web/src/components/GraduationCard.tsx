import { graduationLines, type GraduationLine } from '@fos/domain';
import type { GraduationView } from '@fos/types';
import { Link } from 'react-router-dom';
import { formatDay } from '../content/diario.ts';

/**
 * Próximo grau na home (#193, D75): aulas registradas desde o último grau, contra o mínimo que a
 * academia da pessoa exige.
 *
 * Mora **abaixo** da agenda de revisão (D56c): é motivo para registrar a aula, não o que o app
 * responde. Sem meta configurada não aparece — quem não segue mínimo de aula não precisa de um card
 * vazio pedindo configuração.
 *
 * Informa e não premia: bater o mínimo não muda cor nem vira destaque, e a última linha lembra que
 * quem decide o grau é o professor.
 */
export function GraduationCard({ graduation }: { graduation: GraduationView | null }) {
  const goal = graduation?.goal;
  const classes = graduation?.classes;
  if (!goal || !classes) return null;

  const linhas = graduationLines(goal, {
    total: classes.total ?? 0,
    gi: classes.gi ?? 0,
    nogi: classes.nogi ?? 0,
    unspecified: classes.unspecified ?? 0,
  });
  const semMarcacao = classes.unspecified ?? 0;

  return (
    <section className="card graduation" aria-labelledby="graduacao-titulo">
      <header className="card__header">
        <h2 id="graduacao-titulo">Próximo grau{goal.nextLabel ? `: ${goal.nextLabel}` : ''}</h2>
        <Link to="/conta" className="diary__cta">
          Ajustar
        </Link>
      </header>
      <p className="hint">
        Aulas registradas no diário desde {formatDay(goal.lastGraduationOn)}
        {goal.currentLabel ? ` (último grau: ${goal.currentLabel})` : ''}.
      </p>
      <dl className="account graduation__lines">
        <Line label="Aulas" line={linhas.total} />
        {(goal.minGi != null || linhas.gi.done > 0) && <Line label="Com kimono" line={linhas.gi} />}
        {(goal.minNogi != null || linhas.nogi.done > 0) && (
          <Line label="Sem kimono" line={linhas.nogi} />
        )}
      </dl>
      {semMarcacao > 0 && (
        <p className="hint">
          {semMarcacao === 1
            ? '1 aula sem dizer se foi com ou sem kimono conta só no total.'
            : `${semMarcacao} aulas sem dizer se foram com ou sem kimono contam só no total.`}
        </p>
      )}
      <p className="hint">
        Conta só o que está registrado como <em>Aula</em>. Mínimo de aulas não é graduação: quem
        decide o grau é o professor.
      </p>
    </section>
  );
}

function Line({ label, line }: { label: string; line: GraduationLine }) {
  return (
    <>
      <dt>{label}</dt>
      <dd>
        <strong>{line.done}</strong>
        {line.minimum !== null && (
          <>
            {' '}
            de {line.minimum}
            {line.remaining !== null && line.remaining > 0 && (
              <span className="hint"> — faltam {line.remaining}</span>
            )}
          </>
        )}
      </dd>
    </>
  );
}
