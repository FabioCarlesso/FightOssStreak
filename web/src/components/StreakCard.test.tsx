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

  it('com o perdão desligado não fala de freeze', () => {
    render(<StreakCard streak={{ ...BASE, freezesPerMonth: 0, freezesRemaining: 0 }} />);

    expect(screen.queryByText(/freeze/i)).not.toBeInTheDocument();
  });
});
