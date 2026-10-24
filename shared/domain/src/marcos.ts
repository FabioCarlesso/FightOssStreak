/**
 * Marcos de streak (#101): 7, 30, 100 e 365 dias seguidos.
 *
 * Lista fixa de apresentação, e não currículo — por isso mora aqui e não em `curriculum/*.json`.
 * Fica em `shared/domain` para o web e o app reconhecerem exatamente os mesmos dias, como a prévia
 * do SRS (`previsao.ts`).
 *
 * O marco **informa, não premia**: uma linha no cartão do streak, sem som, animação nem pontos. A
 * gamificação que se sustenta sozinha é o critério de falha do `docs/produto/mvp-web.md`.
 */
export const STREAK_MILESTONES: readonly number[] = [7, 30, 100, 365];

/**
 * O marco atingido **hoje**, ou `null`.
 *
 * Exige o treino de hoje registrado porque o streak ancora em ontem enquanto o dia não acaba: um
 * `currentStreak` de 7 sem registro hoje é um marco que foi atingido **ontem**, e a issue pede o
 * reconhecimento no dia em que ele acontece. Sem estado guardado: no dia seguinte a corrente vale 8
 * e a linha some sozinha.
 */
export function streakMilestoneToday(currentStreak: number, drilledToday: boolean): number | null {
  if (!drilledToday) return null;
  return STREAK_MILESTONES.includes(currentStreak) ? currentStreak : null;
}
