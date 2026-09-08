import type { StreakHistory } from '@fos/types';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StreakHeatmap } from './StreakHeatmap.tsx';

/**
 * O heatmap da home (#102).
 *
 * A grade em si é testada em `shared/domain` (`heatmap.test.ts`). O que se testa aqui é o que a
 * pessoa lê e o que o leitor de tela anuncia: dia com treino distinguível de dia sem, lacuna
 * preservada quando a corrente quebrou e retomou, e dia perdoado marcado sem virar dia de treino.
 */

// 2026-08-16 é um domingo; o período começa num domingo por construção da grade.
const HISTORICO: StreakHistory = {
  from: '2026-07-26',
  to: '2026-08-16',
  days: [
    { day: '2026-08-01', count: 1, frozen: false },
    { day: '2026-08-02', count: 2, frozen: false },
    // lacuna de 03 a 14: a corrente morreu aqui e foi retomada
    { day: '2026-08-15', count: 0, frozen: true },
    { day: '2026-08-16', count: 3, frozen: false },
  ],
};

describe('heatmap de streak', () => {
  it('desenha uma célula por dia do período, e nenhuma para dia futuro', () => {
    const { container } = render(<StreakHeatmap historico={HISTORICO} />);

    // Quatro semanas de sete dias, menos os seis dias posteriores a hoje (domingo).
    expect(container.querySelectorAll('.heatmap__grid .heatmap__cell')).toHaveLength(28);
    expect(container.querySelectorAll('.heatmap__cell--future')).toHaveLength(6);
  });

  it('dia com treino é visualmente distinto de dia sem treino', () => {
    render(<StreakHeatmap historico={HISTORICO} />);

    expect(screen.getByTitle('01/08 — 1 registro')).toHaveClass('heatmap__cell--1');
    expect(screen.getByTitle('02/08 — 2 registros')).toHaveClass('heatmap__cell--2');
    expect(screen.getByTitle('16/08 — 3 registros')).toHaveClass('heatmap__cell--3');
    expect(screen.getByTitle('05/08 — sem registro')).toHaveClass('heatmap__cell--0');
  });

  it('mostra o histórico bruto, e não a corrente: a lacuna do meio continua lá', () => {
    render(<StreakHeatmap historico={HISTORICO} />);

    // A corrente atual vale 1 (só hoje, com o dia 15 coberto), mas o mês inteiro está na tela.
    expect(screen.getByTitle('03/08 — sem registro')).toBeInTheDocument();
    expect(screen.getByTitle('10/08 — sem registro')).toBeInTheDocument();
    expect(screen.getByTitle('01/08 — 1 registro')).toBeInTheDocument();
  });

  it('dia perdoado por freeze é marcado sem entrar na escala de treino', () => {
    render(<StreakHeatmap historico={HISTORICO} />);

    const perdoado = screen.getByTitle('15/08 — dia perdoado por freeze');
    expect(perdoado).toHaveClass('heatmap__cell--frozen');
    expect(perdoado).not.toHaveClass('heatmap__cell--0');
  });

  it('anuncia o período e a contagem para quem não vê a grade', () => {
    render(<StreakHeatmap historico={HISTORICO} />);

    expect(
      screen.getByRole('img', { name: '3 dias com treino registrado entre 26/07 e 16/08' }),
    ).toBeInTheDocument();
  });

  it('marca os meses uma vez só, na coluna em que eles viram', () => {
    render(<StreakHeatmap historico={HISTORICO} />);

    expect(screen.getAllByText('jul')).toHaveLength(1);
    expect(screen.getAllByText('ago')).toHaveLength(1);
  });

  it('sem período não desenha nada, em vez de inventar uma grade', () => {
    const { container } = render(<StreakHeatmap historico={{ days: [] }} />);

    expect(container.querySelector('.heatmap')).toBeNull();
  });
});
