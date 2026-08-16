import type { StreakView } from '@fos/types';

/**
 * Streak com contexto.
 *
 * Mostra "dias ativos nos últimos 30" ao lado do streak porque essa é a métrica que responde ao
 * critério de sucesso do MVP (≥ 12 de 30). O streak sozinho mede só continuidade — e, segundo o
 * critério de falha declarado, um streak que se sustenta sozinho é sinal ruim, não bom.
 */
export function StreakCard({ streak }: { streak: StreakView }) {
  const active = streak.activeDaysLast30 ?? 0;
  const target = streak.targetDaysLast30 ?? 12;
  const progress = Math.min(100, Math.round((active / target) * 100));

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
      </div>
    </section>
  );
}
