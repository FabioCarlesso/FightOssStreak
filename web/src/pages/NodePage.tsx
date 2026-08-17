import { Link, useParams } from 'react-router-dom';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';
import { useDemoMode } from '../state/demoMode.ts';
import { DrillForm } from '../components/DrillForm.tsx';
import { QuizForm } from '../components/QuizForm.tsx';
import { VideoEmbed } from '../components/VideoEmbed.tsx';

/** Detalhe do nó: conceito, vídeo, quiz conceitual e registro de drill. */
export function NodePage() {
  const { code = '' } = useParams();
  const node = useAsync(() => api.getNode(code), [code]);
  const demo = useDemoMode();

  // Só mostra o carregamento na primeira visita: em recargas os dados anteriores continuam na
  // tela, senão o resultado do quiz seria desmontado no instante em que aparece.
  if (node.loading && !node.data) return <p className="empty">Carregando nó…</p>;
  if (node.error && !node.data) return <p className="error">{node.error.message}</p>;
  if (!node.data) return null;

  const detail = node.data;
  const lockedByProgress = detail.status === 'LOCKED';
  // Nó bloqueado aberto em demonstração: o conteúdo aparece, mas em leitura. Deixar gravar aqui
  // concluiria o nó, destravaria outros de verdade e mexeria em streak e SRS (D20/D31).
  const preview = lockedByProgress && demo.enabled;
  const locked = lockedByProgress && !demo.enabled;

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

        {preview && (
          <p className="node__locked node__demo">
            Modo demonstração: este nó continua bloqueado no seu progresso.{' '}
            {detail.unlockRule === 'ANY'
              ? 'Para destravar de verdade, conclua qualquer um dos pré-requisitos abaixo.'
              : 'Para destravar de verdade, conclua todos os pré-requisitos abaixo.'}{' '}
            O quiz aparece só para leitura e o registro de drill fica fora — nada é gravado.
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
        <section className="card">
          <h3>Quiz conceitual</h3>
          <QuizForm
            nodeCode={detail.code ?? code}
            questions={detail.quiz ?? []}
            onDone={node.reload}
            readOnly={preview}
          />
        </section>
      )}

      {!lockedByProgress && (
        <section className="card">
          <h3>Registrar drill</h3>
          <DrillForm nodeCode={detail.code ?? code} srs={detail.srs} onDone={node.reload} />
        </section>
      )}

      <p className="safety-notice">{detail.safetyNotice}</p>
    </article>
  );
}
