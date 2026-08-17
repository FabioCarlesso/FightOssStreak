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

  // O `useAsync` preserva os dados anteriores de propósito: é o que impede o resultado do quiz de
  // ser desmontado a cada `reload()`. Ao trocar de nó, porém, o dado preservado é de OUTRO nó — e
  // exibi-lo sob a URL nova não mostraria só o conceito errado: o registro de drill ficaria ligado
  // ao código anterior, e um clique nessa janela gravaria no nó errado. Enquanto a resposta do nó
  // atual não chega, a tela volta ao carregamento; em recarga do mesmo nó, nada é desmontado.
  const detail = node.data?.code === code ? node.data : null;

  if (node.error && !detail) return <p className="error">{node.error.message}</p>;
  if (!detail) return <p className="empty">Carregando nó…</p>;

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

      {/*
       * `key` pelo código do nó: os dois formulários guardam estado que só faz sentido para o nó
       * em que foi produzido — a nota do quiz, a auto-avaliação escolhida, a anotação digitada. Sem
       * ele o React reaproveita a instância ao trocar de nó (a rota é a mesma, só o parâmetro muda)
       * e o resultado do nó anterior aparece no seguinte, inclusive num nó bloqueado em
       * demonstração, onde o "nó concluído" seria o oposto do que a tela promete. Recarregar o mesmo
       * nó não muda a `key`, então o resultado do quiz continua sobrevivendo ao `reload()`.
       */}
      {!locked && (
        <section className="card">
          <h3>Quiz conceitual</h3>
          <QuizForm
            key={code}
            nodeCode={code}
            questions={detail.quiz ?? []}
            onDone={node.reload}
            readOnly={preview}
          />
        </section>
      )}

      {!lockedByProgress && (
        <section className="card">
          <h3>Registrar drill</h3>
          <DrillForm key={code} nodeCode={code} srs={detail.srs} onDone={node.reload} />
        </section>
      )}

      <p className="safety-notice">{detail.safetyNotice}</p>
    </article>
  );
}
