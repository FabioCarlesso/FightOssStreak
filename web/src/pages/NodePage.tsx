import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';
import { DrillForm } from '../components/DrillForm.tsx';
import { QuizForm } from '../components/QuizForm.tsx';
import { VideoEmbed } from '../components/VideoEmbed.tsx';

/** Detalhe do nó: conceito, vídeo, quiz conceitual e registro de drill. */
export function NodePage() {
  const { code = '' } = useParams();
  const node = useAsync(() => api.getNode(code), [code]);

  // Só mostra o carregamento na primeira visita: em recargas os dados anteriores continuam na
  // tela, senão o resultado do quiz seria desmontado no instante em que aparece.
  if (node.loading && !node.data) return <p className="empty">Carregando nó…</p>;
  if (node.error && !node.data) return <p className="error">{node.error.message}</p>;
  if (!node.data) return null;

  const detail = node.data;
  const locked = detail.status === 'LOCKED';

  return (
    <article className="stack">
      <section className="card">
        <p className="node__breadcrumb">
          <Link to="/arvore">Árvore</Link> · {detail.moduleCode} {detail.moduleTitle}
        </p>
        <h2 className="node__title">
          <span className="node__code">{detail.code}</span> {detail.title}
        </h2>
        <span className={`belt belt--${(detail.belt ?? 'BRANCA').toLowerCase()}`}>
          {detail.belt}
        </span>

        {locked && (
          <p className="node__locked">
            Nó bloqueado.{' '}
            {detail.unlockRule === 'ANY'
              ? 'Conclua qualquer um dos pré-requisitos abaixo.'
              : 'Conclua todos os pré-requisitos abaixo.'}
          </p>
        )}

        <h3>Conceito</h3>
        <p className="node__concept">{detail.concept}</p>

        {(detail.prereqs?.length ?? 0) > 0 && (
          <>
            <h3>Pré-requisitos</h3>
            <ul className="prereqs">
              {detail.prereqs?.map((prereq) => (
                <li key={prereq.code}>
                  <Link to={`/no/${prereq.code}`}>
                    {prereq.completed ? '✓' : '○'} {prereq.code} — {prereq.title}
                  </Link>
                </li>
              ))}
            </ul>
          </>
        )}
      </section>

      <section className="card">
        <h3>Vídeo de referência</h3>
        <VideoEmbed video={detail.video} title={detail.title} />
      </section>

      {!locked && (
        <>
          <section className="card">
            <h3>Quiz conceitual</h3>
            <QuizForm
              nodeCode={detail.code ?? code}
              questions={detail.quiz ?? []}
              onDone={node.reload}
            />
          </section>

          <section className="card">
            <h3>Registrar drill</h3>
            <DrillForm nodeCode={detail.code ?? code} srs={detail.srs} onDone={node.reload} />
          </section>
        </>
      )}

      <p className="safety-notice">{detail.safetyNotice}</p>
    </article>
  );
}
