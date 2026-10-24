import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import type { DrillResult, DueItem, NodeDetail } from '@fos/types';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';
import { useDemoMode } from '../state/demoMode.ts';
import { DrillForm } from '../components/DrillForm.tsx';
import { ExtraVideos } from '../components/ExtraVideos.tsx';
import { VideoEmbed } from '../components/VideoEmbed.tsx';
import { paragraphsOf } from '../content/conceito.ts';

/** O que aconteceu com o passo que acabou de sair — é o "instante de confirmação" do passo seguinte. */
type Saida =
  { tipo: 'revisado'; code: string; nextReviewOn?: string } | { tipo: 'pulado'; code: string };

/**
 * Sessão guiada de revisão (#47): a agenda de "Revise hoje" percorrida um nó por vez.
 *
 * A agenda é o que o produto promete responder, e como lista de links ela abandonava quem clicava
 * nela — cada revisão terminava na página inteira do nó, sem "faltam 2" e sem caminho para o
 * seguinte. Aqui o percurso é o próprio SRS: o mais atrasado primeiro, progresso visível, avanço
 * depois do registro e uma tela de fim.
 *
 * **A fila é capturada uma vez.** `GET /api/reviews/today` sai do servidor já ordenado e é lido só
 * na abertura: o item registrado deixa de estar vencido, e refazer a chamada a cada passo faria a
 * fila encolher embaixo da pessoa e o "2 de 5" virar mentira. Reabrir a tela é que relê a agenda —
 * e aí o que já foi registrado não volta, que é o certo.
 *
 * Nenhum endpoint novo: cada passo pede o próprio nó, e o seguinte é pedido enquanto o atual está
 * na tela. Só o dado é adiantado — o `<iframe>` do vídeo só monta no passo em que ele aparece, por
 * causa do custo por player que a D32 mediu.
 */
export function ReviewPage() {
  const demo = useDemoMode();

  // Modo demonstração não grava (D31), e uma sessão de revisão que não registra nada não é sessão.
  if (demo.enabled) {
    return (
      <section className="card">
        <h2>Revisar</h2>
        <p className="empty">
          O modo demonstração não grava, e a sessão de revisão existe para registrar. Desligue a
          faixa no topo para revisar de verdade. <Link to="/hoje">Voltar para hoje</Link>.
        </p>
      </section>
    );
  }

  return <ReviewSession />;
}

function ReviewSession() {
  // Sem `reload` em lugar nenhum desta tela: é o que mantém a fila congelada durante a sessão.
  const agenda = useAsync(() => api.getReviewsToday(), []);
  const [passo, setPasso] = useState(0);
  const [revisados, setRevisados] = useState(0);
  const [pulados, setPulados] = useState(0);
  const [saida, setSaida] = useState<Saida | null>(null);

  // Um pedido por nó, compartilhado entre o prefetch e o passo que o usa: o próximo passo encontra
  // a resposta já a caminho (ou pronta) em vez de começar outra.
  const pedidos = useRef(new Map<string, Promise<NodeDetail>>());
  const carregar = useCallback((code: string) => {
    let pedido = pedidos.current.get(code);
    if (!pedido) {
      pedido = api.getNode(code);
      pedidos.current.set(code, pedido);
      // Prefetch que falha não é erro da tela: o passo, quando chegar, mostra o erro dele.
      pedido.catch(() => pedidos.current.delete(code));
    }
    return pedido;
  }, []);

  const fila = agenda.data?.due ?? [];
  const proximo = fila[passo + 1]?.nodeCode;
  useEffect(() => {
    if (proximo) void carregar(proximo).catch(() => undefined);
  }, [proximo, carregar]);

  if (agenda.error) return <p className="error">{agenda.error.message}</p>;
  if (!agenda.data) return <p className="empty">Carregando agenda…</p>;

  if (fila.length === 0) {
    return (
      <section className="card">
        <h2>Revisar</h2>
        <p className="empty">
          Nada vencido hoje — a agenda está em dia. <Link to="/hoje">Voltar para hoje</Link>.
        </p>
      </section>
    );
  }

  if (passo >= fila.length) {
    return <ReviewEnd revisados={revisados} pulados={pulados} />;
  }

  const item = fila[passo]!;

  function avancar(nova: Saida) {
    setSaida(nova);
    setPasso((atual) => atual + 1);
    // Registrar e pular ficam no pé do passo: sem voltar ao topo, o passo seguinte abria já no
    // formulário de drill, com o "N de M", a confirmação e o conceito fora da tela.
    window.scrollTo({ top: 0 });
  }

  return (
    <div className="stack">
      <section className="card review__bar">
        <p className="review__progress">
          <strong>
            {passo + 1} de {fila.length}
          </strong>{' '}
          na revisão de hoje
        </p>
        {saida && <ConfirmacaoDaSaida saida={saida} />}
      </section>

      {/* `key` pelo código: o passo guarda o nó carregado e o formulário de drill, e os dois são
          de um nó só (D31). Reaproveitar a instância foi o que, na tela do nó, fez o resultado
          do anterior aparecer no seguinte e o formulário gravar no código errado. */}
      <ReviewStep
        key={item.nodeCode}
        item={item}
        carregar={carregar}
        onRegistrado={(resultado) => {
          setRevisados((n) => n + 1);
          avancar({
            tipo: 'revisado',
            code: item.nodeCode ?? '',
            nextReviewOn: resultado.nextReviewOn,
          });
        }}
        onPular={() => {
          setPulados((n) => n + 1);
          avancar({ tipo: 'pulado', code: item.nodeCode ?? '' });
        }}
      />
    </div>
  );
}

function ConfirmacaoDaSaida({ saida }: { saida: Saida }) {
  if (saida.tipo === 'pulado') {
    return <p className="hint">{saida.code} pulado — nada foi gravado.</p>;
  }
  return (
    <p className="drill__result">
      ✓ {saida.code} registrado. Próxima revisão em <strong>{saida.nextReviewOn}</strong>.
    </p>
  );
}

function ReviewStep({
  item,
  carregar,
  onRegistrado,
  onPular,
}: {
  item: DueItem;
  carregar: (code: string) => Promise<NodeDetail>;
  onRegistrado: (resultado: DrillResult) => void;
  onPular: () => void;
}) {
  const code = item.nodeCode ?? '';
  const node = useAsync(() => carregar(code), [code]);
  // Mesma guarda da `NodePage`: dado de outro nó nunca é exibido sob este código.
  const detail = node.data?.code === code ? node.data : null;

  return (
    <article className="stack">
      <section className="card">
        <header className="card__header">
          <h2 className="node__title">
            <span className="node__code">{code}</span> {item.title}
          </h2>
          <span className={(item.daysOverdue ?? 0) > 0 ? 'pill pill--late' : 'pill'}>
            {formatOverdue(item.daysOverdue ?? 0)}
          </span>
        </header>

        {node.error && !detail && <p className="error">{node.error.message}</p>}
        {!detail && !node.error && <p className="empty">Carregando nó…</p>}

        {detail && (
          <>
            <h3>Conceito</h3>
            {paragraphsOf(detail.concept).map((paragraph, index) => (
              <p key={index} className="node__concept">
                {paragraph}
              </p>
            ))}
          </>
        )}

        <p className="hint">
          <Link to={`/no/${code}`}>Abrir o nó completo</Link> — quiz, pré-requisitos e anotações.
        </p>
      </section>

      {detail && (
        <>
          <section className="card">
            <h3>Vídeo de referência</h3>
            <VideoEmbed video={detail.video} title={detail.title} />
          </section>

          <ExtraVideos videos={detail.extraVideos} />

          <section className="card">
            <h3>Registrar drill</h3>
            {detail.status === 'LOCKED' ? (
              <p className="empty">
                Este nó está bloqueado no seu progresso e não recebe registro.
              </p>
            ) : (
              <DrillForm key={code} nodeCode={code} srs={detail.srs} onDone={onRegistrado} />
            )}
          </section>
        </>
      )}

      <p className="review__skip">
        <button type="button" className="link-button" onClick={onPular}>
          Pular este nó
        </button>{' '}
        <span className="hint">— não grava nada e não mexe na agenda.</span>
      </p>
    </article>
  );
}

function ReviewEnd({ revisados, pulados }: { revisados: number; pulados: number }) {
  // Lido no fim, e não somado dos resultados: é o número que a tela inicial vai mostrar.
  const streak = useAsync(() => api.getStreak(), []);

  return (
    <section className="card">
      <h2>Revisão concluída</h2>
      <p>
        <strong>{revisados}</strong> {revisados === 1 ? 'revisado' : 'revisados'} ·{' '}
        <strong>{pulados}</strong> {pulados === 1 ? 'pulado' : 'pulados'}
      </p>
      {streak.data && (
        <p className="hint">
          Streak: <strong>{streak.data.currentStreak ?? 0}</strong>{' '}
          {streak.data.currentStreak === 1 ? 'dia seguido' : 'dias seguidos'}.
        </p>
      )}
      <p>
        <Link to="/hoje">Voltar para hoje</Link>
      </p>
    </section>
  );
}

function formatOverdue(days: number): string {
  if (days <= 0) return 'para hoje';
  if (days === 1) return '1 dia atrasado';
  return `${days} dias atrasado`;
}
