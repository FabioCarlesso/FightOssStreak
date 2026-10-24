import { render, screen } from '@testing-library/react-native';

import { CartaoStreak } from './CartaoStreak';

/**
 * Marcos de streak (#101) no app. A regra mora em `@fos/domain` e é testada lá; aqui, só que o
 * cartão diz o marco no dia certo, com a mesma frase do web, e cala nos outros dias.
 */
const BASE = {
  currentStreak: 4,
  longestStreak: 4,
  drilledToday: true,
  activeDaysLast30: 9,
  targetDaysLast30: 12,
};

describe('marco de streak no cartão', () => {
  it.each([7, 30, 100, 365])(
    'com %i dias e o treino de hoje registrado, diz o marco',
    async (dias) => {
      await render(<CartaoStreak streak={{ ...BASE, currentStreak: dias, longestStreak: dias }} />);

      expect(screen.getByTestId('streak-marco')).toHaveTextContent(
        `Marco de ${dias} dias seguidos, completado hoje.`,
      );
    },
  );

  it('fora dos marcos não fala de marco', async () => {
    await render(<CartaoStreak streak={{ ...BASE, currentStreak: 8, longestStreak: 8 }} />);

    expect(screen.queryByTestId('streak-marco')).toBeNull();
  });

  it('com 7 dias mas sem treino hoje, o marco foi ontem e não aparece', async () => {
    await render(<CartaoStreak streak={{ ...BASE, currentStreak: 7, drilledToday: false }} />);

    expect(screen.queryByTestId('streak-marco')).toBeNull();
  });
});
