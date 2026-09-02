import type { StreakView } from '@fos/types';

/**
 * Streak com contexto.
 *
 * Mostra "dias ativos nos últimos 30" ao lado do streak porque essa é a métrica que responde ao
 * critério de sucesso do MVP (≥ 12 de 30). O streak sozinho mede só continuidade — e, segundo o
 * critério de falha declarado, um streak que se sustenta sozinho é sinal ruim, não bom.
 *
 * O saldo de freeze (#99) fica junto do contador, e não escondido em outra tela, porque a única
 * hora em que ele importa é a hora em que a pessoa olha a sequência e conta os dias.
 */
export function StreakCard({ streak }: { streak: StreakView }) {
  const active = streak.activeDaysLast30 ?? 0;
  const target = streak.targetDaysLast30 ?? 12;
  const progress = Math.min(100, Math.round((active / target) * 100));

  // Zero desliga o perdão nesta instalação: sem saldo cheio não há nada a dizer sobre freeze.
  const freezesPerMonth = streak.freezesPerMonth ?? 0;
  const freezesRemaining = streak.freezesRemaining ?? 0;

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
    </section>
  );
}

/** `YYYY-MM-DD` como dia e mês. Sem `Date`: a data é de calendário e fuso não tem o que dizer. */
function formatDay(day: string): string {
  const [, month, date] = day.split('-');
  return `${date}/${month}`;
}
