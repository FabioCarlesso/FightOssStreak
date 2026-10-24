import type { StreakView } from '@fos/types';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StreakCard } from './StreakCard.tsx';

/**
 * O saldo de freeze (#99) na home.
 *
 * A regra em si é testada em `shared/domain` e espelhada no backend (D17). O que se testa aqui é o
 * que a pessoa lê: quanto sobra, e se um dia perdido foi coberto — e que a instalação com o perdão
 * desligado (`freezesPerMonth: 0`) não fala de freeze nenhum.
 */
const BASE: StreakView = {
  currentStreak: 4,
  longestStreak: 4,
  drilledToday: true,
  activeDaysLast30: 9,
  targetDaysLast30: 12,
  today: '2026-08-16',
  freezesPerMonth: 2,
  freezesRemaining: 2,
};

describe('saldo de freeze na home', () => {
  it('mostra quantos freezes restam no mês corrente', () => {
    render(<StreakCard streak={BASE} />);

    expect(screen.getByText(/2 de 2 freezes neste mês/i)).toBeInTheDocument();
  });

  it('avisa quando um freeze cobriu um dia perdido, com a data que ele cobriu', () => {
    render(<StreakCard streak={{ ...BASE, freezesRemaining: 1, lastFrozenOn: '2026-08-15' }} />);

    expect(screen.getByText(/1 de 2 freezes neste mês/i)).toBeInTheDocument();
    expect(screen.getByText(/um cobriu 15\/08 e a sequência seguiu/i)).toBeInTheDocument();
  });

  it('sem histórico carregado o cartão aparece sozinho, sem grade vazia', () => {
    const { container } = render(<StreakCard streak={BASE} />);

    // O heatmap (#102) chega numa segunda chamada, depois do streak: enquanto ela não volta o
    // cartão precisa renderizar inteiro, e não segurar a tela esperando a grade.
    expect(container.querySelector('.heatmap')).toBeNull();
    expect(screen.getByText(/4/)).toBeInTheDocument();
  });

  it('com histórico, a grade entra no mesmo cartão — a agenda não é empurrada para baixo', () => {
    const { container } = render(
      <StreakCard
        streak={BASE}
        historico={{
          from: '2026-08-09',
          to: '2026-08-16',
          days: [{ day: '2026-08-16', count: 1 }],
        }}
      />,
    );

    expect(container.querySelector('.streak .heatmap')).not.toBeNull();
  });

  it('com o perdão desligado não fala de freeze', () => {
    render(<StreakCard streak={{ ...BASE, freezesPerMonth: 0, freezesRemaining: 0 }} />);

    expect(screen.queryByText(/freeze/i)).not.toBeInTheDocument();
  });
});

/**
 * Marcos de streak (#101). A regra (quais números, e por que exige o treino de hoje) é testada em
 * `shared/domain`; aqui, só que a home diz o marco no dia certo e cala nos outros.
 */
describe('marco de streak na home', () => {
  it.each([7, 30, 100, 365])('com %i dias e o treino de hoje registrado, diz o marco', (dias) => {
    render(<StreakCard streak={{ ...BASE, currentStreak: dias, longestStreak: dias }} />);

    expect(
      screen.getByText(`Marco de ${dias} dias seguidos, completado hoje.`),
    ).toBeInTheDocument();
  });

  it.each([6, 8, 29, 31])('com %i dias não fala de marco', (dias) => {
    render(<StreakCard streak={{ ...BASE, currentStreak: dias, longestStreak: dias }} />);

    expect(screen.queryByText(/marco de/i)).not.toBeInTheDocument();
  });

  it('com 7 dias mas sem treino hoje, o marco foi ontem e não aparece', () => {
    render(<StreakCard streak={{ ...BASE, currentStreak: 7, drilledToday: false }} />);

    expect(screen.queryByText(/marco de/i)).not.toBeInTheDocument();
  });
});
