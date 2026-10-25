import { streakMilestoneToday } from '@fos/domain';
import type { StreakHistory, StreakView } from '@fos/types';
import { useState } from 'react';
import { baixarImagemDoMarco } from './baixarImagemDoMarco.ts';
import { comporImagemDoMarco } from './imagemDoMarco.ts';
import { StreakHeatmap } from './StreakHeatmap.tsx';

/**
 * Streak com contexto.
 *
 * Mostra "dias ativos nos últimos 30" ao lado do streak porque essa é a métrica que responde ao
 * critério de sucesso do MVP (≥ 12 de 30). O streak sozinho mede só continuidade — e, segundo o
 * critério de falha declarado, um streak que se sustenta sozinho é sinal ruim, não bom.
 *
 * O saldo de freeze (#99) fica junto do contador, e não escondido em outra tela, porque a única
 * hora em que ele importa é a hora em que a pessoa olha a sequência e conta os dias.
 *
 * O heatmap (#102) entra no mesmo cartão pelo mesmo motivo, e não em seção própria: seção nova
 * empurraria a agenda de revisão para baixo, e a agenda no topo é decisão declarada (D56c). Ele é
 * histórico do mesmo assunto — a corrente —, então mora junto dela.
 */
export function StreakCard({
  streak,
  historico,
}: {
  streak: StreakView;
  historico?: StreakHistory | null;
}) {
  const active = streak.activeDaysLast30 ?? 0;
  const target = streak.targetDaysLast30 ?? 12;
  const progress = Math.min(100, Math.round((active / target) * 100));

  // Zero desliga o perdão nesta instalação: sem saldo cheio não há nada a dizer sobre freeze.
  const freezesPerMonth = streak.freezesPerMonth ?? 0;
  const freezesRemaining = streak.freezesRemaining ?? 0;

  // Uma linha dentro do cartão, e não modal nem banner: o marco informa, não premia (#101). Por
  // isso também não há "já visto" guardado — rever a mesma frase no mesmo dia não incomoda, e no
  // dia seguinte a corrente vale outro número e ela some sozinha.
  const milestone = streakMilestoneToday(streak.currentStreak ?? 0, streak.drilledToday ?? false);
  const [falhaNaImagem, setFalhaNaImagem] = useState(false);

  // A imagem (#106) é oferecida só no dia do marco, junto da linha dele, e não todo dia: um botão
  // permanente transformaria cada dia de corrente em troféu a exibir — a gamificação que o
  // `docs/produto/mvp-web.md` declara como falha. Gerada inteira no navegador.
  async function baixarImagem(marco: number) {
    setFalhaNaImagem(false);
    try {
      await baixarImagemDoMarco(comporImagemDoMarco(streak.currentStreak ?? 0, marco));
    } catch {
      setFalhaNaImagem(true);
    }
  }

  return (
    <section className="card streak">
      <div className="streak__main">
        <span className="streak__flame" aria-hidden="true">
          🔥
        </span>
        <div>
          <p className="streak__count">
            {streak.currentStreak ?? 0}
            <span className="streak__unit">
              {streak.currentStreak === 1 ? ' dia' : ' dias'} seguidos
            </span>
          </p>
          <p className="streak__hint">
            {streak.drilledToday
              ? 'Treino de hoje já registrado.'
              : 'Ainda sem registro hoje — o streak se mantém até o fim do dia.'}
          </p>
          {milestone !== null && (
            <p className="streak__milestone">
              <span>Marco de {milestone} dias seguidos, completado hoje.</span>{' '}
              <button
                type="button"
                className="link-button"
                onClick={() => void baixarImagem(milestone)}
              >
                Baixar imagem
              </button>
            </p>
          )}
          {milestone !== null && falhaNaImagem && (
            <p className="streak__hint" role="status">
              Não foi possível gerar a imagem neste navegador.
            </p>
          )}
        </div>
      </div>

      <div className="streak__meta">
        <div className="streak__bar" role="img" aria-label={`${active} de ${target} dias na meta`}>
          <span style={{ width: `${progress}%` }} />
        </div>
        <p>
          <strong>{active}</strong> de {target} dias com treino registrado nos últimos 30
        </p>
        {(streak.longestStreak ?? 0) > (streak.currentStreak ?? 0) && (
          <p className="streak__record">Recorde: {streak.longestStreak} dias</p>
        )}
        {freezesPerMonth > 0 && (
          <p className="streak__freeze">
            <span aria-hidden="true">🧊</span> {freezesRemaining} de {freezesPerMonth}{' '}
            {freezesPerMonth === 1 ? 'freeze' : 'freezes'} neste mês
            {streak.lastFrozenOn
              ? ` — um cobriu ${formatDay(streak.lastFrozenOn)} e a sequência seguiu.`
              : '. Um dia perdido é perdoado enquanto houver saldo.'}
          </p>
        )}
      </div>

      {historico && <StreakHeatmap historico={historico} />}
    </section>
  );
}

/** `YYYY-MM-DD` como dia e mês. Sem `Date`: a data é de calendário e fuso não tem o que dizer. */
function formatDay(day: string): string {
  const [, month, date] = day.split('-');
  return `${date}/${month}`;
}
