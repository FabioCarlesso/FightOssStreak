import type { CountedMetric, SrsAdherence } from '@fos/types';
import { api } from '../api/client.ts';
import { useAsync } from '../state/useAsync.ts';

/**
 * Os quatro critérios de sucesso do MVP, medidos (docs/05-mvp-web-plano.md).
 *
 * A tela existe para responder uma pergunta só, ao fim dos 30 dias: a mecânica funcionou? Por isso
 * cada métrica aparece ao lado da meta e diz atingiu/não atingiu — inclusive quando a resposta é
 * ruim. Um painel que só mostra número bonito não serviria ao critério de falha declarado no plano.
 */
export function ProgressPage() {
  const metrics = useAsync(() => api.getMvpMetrics(), []);

  if (metrics.loading && !metrics.data) return <p className="empty">Carregando métricas…</p>;
  if (metrics.error && !metrics.data) return <p className="error">{metrics.error.message}</p>;
  if (!metrics.data) return null;

  const data = metrics.data;

  return (
    <div className="stack">
      <section className="card">
        <header className="card__header">
          <h2>Critérios de sucesso</h2>
          <span className="badge">{data.windowDays} dias</span>
        </header>
        <p className="hint">
          Janela de {data.windowStart} a {data.windowEnd}.{' '}
          {data.targetsFor30Days
            ? 'As metas são as de docs/05 e valem para 30 dias.'
            : 'Fora da janela de 30 dias — as metas de docs/05 não se aplicam a este recorte.'}
        </p>
      </section>

      <MetricCard
        title="Dias com registro de drill"
        metric={data.daysWithDrill}
        value={`${data.daysWithDrill?.value ?? 0} de ${data.windowDays}`}
        question="O hábito de registrar sobrevive à rotina?"
      />

      <AdherenceCard adherence={data.srsAdherence} />

      <MetricCard
        title="Nós concluídos"
        metric={data.nodesCompleted}
        value={`${data.nodesCompleted?.value ?? 0}`}
        question="O currículo acompanha o que aparece na aula?"
      />

      <MetricCard
        title="Nós com quiz refeito"
        metric={data.quizRetakes}
        value={`${data.quizRetakes?.value ?? 0}`}
        question="Refazer quiz por conta própria é sinal de retenção real."
      />

      <p className="hint">
        Se ao fim dos 30 dias o app estiver sendo aberto só para não perder o streak, sem que a
        agenda de revisão mude o que se treina no tatame, a mecânica falhou — e é isso que estes
        números precisam conseguir dizer.
      </p>
    </div>
  );
}

function MetricCard({
  title,
  metric,
  value,
  question,
}: {
  title: string;
  metric?: CountedMetric;
  value: string;
  question: string;
}) {
  return (
    <section className="card metric">
      <header className="card__header">
        <h3>{title}</h3>
        <MetStamp met={metric?.met ?? false} />
      </header>
      <p className="metric__value">{value}</p>
      <p className="metric__target">Meta: {metric?.target ?? 0}</p>
      <p className="hint">{question}</p>
    </section>
  );
}

function AdherenceCard({ adherence }: { adherence?: SrsAdherence }) {
  // `percent` nulo não é zero: nada venceu na janela, então não houve sugestão a ignorar.
  const percent = adherence?.percent ?? null;

  return (
    <section className="card metric">
      <header className="card__header">
        <h3>Revisões atendidas via SRS</h3>
        {percent === null ? <span className="pill">sem agenda</span> : <MetStamp met={adherence?.met ?? false} />}
      </header>
      <p className="metric__value">
        {percent === null ? '—' : `${percent}%`}
        {percent !== null && (
          <span className="metric__detail">
            {' '}
            ({adherence?.attended ?? 0} de {adherence?.scheduled ?? 0})
          </span>
        )}
      </p>
      <p className="metric__target">Meta: {adherence?.targetPercent ?? 60}%</p>
      <p className="hint">
        {percent === null
          ? 'Nenhuma revisão venceu nesta janela — sem agenda, não há aderência a medir.'
          : 'A sugestão de "o que drillar" é usada ou ignorada?'}
      </p>
    </section>
  );
}

function MetStamp({ met }: { met: boolean }) {
  return <span className={met ? 'pill pill--met' : 'pill pill--late'}>{met ? 'atingido' : 'em aberto'}</span>;
}
