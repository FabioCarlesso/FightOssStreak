import { buildStreakHeatmap, daysBetween, type ActivityDay, type HeatmapCell } from '@fos/domain';
import type { StreakHistory } from '@fos/types';
import { useEffect, useRef } from 'react';

/**
 * Heatmap dos dias com registro (#102).
 *
 * Existe porque o número do streak responde uma coisa só — "a corrente está viva?" — e a pergunta
 * que sustenta hábito é outra: "como foram os últimos meses?". Quem quebrou e retomou não tem nada
 * a ver no contador, e é justamente essa pessoa que precisa ver que voltou.
 *
 * **Não é uma segunda contagem.** Os dias vêm do backend, do mesmo conjunto que a corrente conta
 * (D58): sessões que não são `DESCANSO` mais os drills avulsos. A grade é montada por
 * `buildStreakHeatmap` em `shared/domain` — calendário puro, sem fuso, reaproveitado no mobile.
 *
 * É **agregado por dia** de propósito: por nó ficou fora de escopo na issue, e detalhar por técnica
 * transformaria a tela inicial num relatório em vez do painel de "e aí, como estou?".
 */
const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

export function StreakHeatmap({ historico }: { historico: StreakHistory }) {
  const scroll = useRef<HTMLDivElement>(null);

  // Meio ano não cabe na largura do celular, e quem rola sozinho é o invólucro. Sem isto a grade
  // abre no passado remoto — mês nenhum preenchido — e a pessoa precisa arrastar para achar a
  // semana em que treinou. O fim é o que importa: o heatmap responde "como andaram os últimos
  // tempos", e a resposta está na borda direita. Antes do `return null` porque hook não pode ficar
  // depois de uma saída antecipada.
  useEffect(() => {
    const caixa = scroll.current;
    if (caixa) caixa.scrollLeft = caixa.scrollWidth;
  }, [historico]);

  const from = historico.from;
  const to = historico.to;
  if (!from || !to) return null;

  const registros: ActivityDay[] = (historico.days ?? [])
    .filter((dia): dia is { day: string; count?: number; frozen?: boolean } => Boolean(dia.day))
    .map((dia) => ({ day: dia.day, count: dia.count ?? 0, frozen: dia.frozen ?? false }));

  // As colunas saem do período que o backend devolveu, e não de uma constante daqui: mudar a
  // janela padrão do endpoint não pode exigir mexer na tela para a grade continuar batendo.
  const semanas = Math.ceil((daysBetween(from, to) + 1) / 7);
  const mapa = buildStreakHeatmap(registros, to, semanas);

  const rotulo =
    `${mapa.activeDays} ${mapa.activeDays === 1 ? 'dia' : 'dias'} com treino registrado` +
    ` entre ${curto(mapa.from)} e ${curto(mapa.to)}`;

  return (
    <div className="heatmap">
      <div className="heatmap__scroll" ref={scroll}>
        <div className="heatmap__months" aria-hidden="true">
          {mapa.weeks.map((semana, coluna) => (
            <span key={semana[0]?.day ?? coluna} className="heatmap__month">
              {rotuloDoMes(mapa.weeks, coluna)}
            </span>
          ))}
        </div>

        <div className="heatmap__grid" role="img" aria-label={rotulo}>
          {mapa.weeks.map((semana, coluna) => (
            <div key={semana[0]?.day ?? coluna} className="heatmap__week">
              {semana.map((celula, linha) =>
                celula === null ? (
                  // Dia que ainda não chegou não é dia sem treino: fica invisível, e não vazio.
                  <span key={linha} className="heatmap__cell heatmap__cell--future" />
                ) : (
                  <span
                    key={celula.day}
                    className={classeDaCelula(celula)}
                    title={tituloDaCelula(celula)}
                  />
                ),
              )}
            </div>
          ))}
        </div>
      </div>

      <p className="heatmap__legend">
        <span>{rotulo}.</span>
        <span className="heatmap__scale" aria-hidden="true">
          menos
          <span className="heatmap__cell heatmap__cell--0" />
          <span className="heatmap__cell heatmap__cell--1" />
          <span className="heatmap__cell heatmap__cell--2" />
          <span className="heatmap__cell heatmap__cell--3" />
          mais
        </span>
      </p>
    </div>
  );
}

/** Só a coluna em que o mês vira ganha rótulo — repetir "ago" seis vezes é ruído, não referência. */
function rotuloDoMes(
  weeks: ReadonlyArray<ReadonlyArray<HeatmapCell | null>>,
  coluna: number,
): string {
  const primeiro = weeks[coluna]?.[0]?.day;
  if (!primeiro) return '';
  const mes = primeiro.slice(5, 7);
  if (coluna === 0) return MESES[Number(mes) - 1] ?? '';
  const anterior = weeks[coluna - 1]?.[0]?.day;
  return anterior && anterior.slice(5, 7) !== mes ? (MESES[Number(mes) - 1] ?? '') : '';
}

function classeDaCelula(celula: HeatmapCell): string {
  // Perdoado vem antes da intensidade porque dia coberto tem `level` 0: sem esta ordem ele se
  // pintaria de falta, que é o oposto do que o freeze fez com aquele dia (D55).
  const tom = celula.frozen ? 'heatmap__cell--frozen' : `heatmap__cell--${celula.level}`;
  return `heatmap__cell ${tom}`;
}

function tituloDaCelula(celula: HeatmapCell): string {
  const dia = curto(celula.day);
  if (celula.frozen) return `${dia} — dia perdoado por freeze`;
  if (celula.count === 0) return `${dia} — sem registro`;
  return `${dia} — ${celula.count} ${celula.count === 1 ? 'registro' : 'registros'}`;
}

/** `YYYY-MM-DD` como dia e mês. Sem `Date`: a data é de calendário e fuso não tem o que dizer. */
function curto(day: string): string {
  const [, mes, dia] = day.split('-');
  return `${dia}/${mes}`;
}
