import type { DrillEntry } from '@fos/types';
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { DrillHistory } from './DrillHistory.tsx';

/**
 * O histórico é a metade que faltava da anotação: o campo já gravava, e o texto nunca voltava para
 * a tela. Os dois casos que decidem se ele é legível são registro com nota e registro sem nota —
 * sumir com o segundo abriria buracos na frequência, e renderizá-lo como nota vazia sujaria a
 * lista com linhas em branco.
 */
describe('DrillHistory', () => {
  it('mostra data, auto-avaliação e a anotação de cada registro', () => {
    const entries: DrillEntry[] = [
      { drilledOn: '2026-08-16', recall: 'HARD', note: 'travei na entrada' },
      { drilledOn: '2026-08-10', recall: 'EASY', note: 'saiu no primeiro' },
    ];

    render(<DrillHistory entries={entries} />);

    expect(screen.getByText('16/08/2026')).toBeInTheDocument();
    expect(screen.getByText('Travei')).toBeInTheDocument();
    expect(screen.getByText('travei na entrada')).toBeInTheDocument();
    expect(screen.getByText('10/08/2026')).toBeInTheDocument();
    expect(screen.getByText('Saiu limpo')).toBeInTheDocument();
    expect(screen.getByText('saiu no primeiro')).toBeInTheDocument();
  });

  it('registro sem anotação continua no histórico, sem linha de nota vazia', () => {
    const { container } = render(
      <DrillHistory entries={[{ drilledOn: '2026-08-16', recall: 'OK' }]} />,
    );

    expect(screen.getByText('16/08/2026')).toBeInTheDocument();
    expect(screen.getByText('Saiu')).toBeInTheDocument();
    expect(container.querySelector('.drill-history__note')).toBeNull();
  });

  it('nó sem registro nenhum mostra estado vazio, não lista quebrada', () => {
    const { container } = render(<DrillHistory entries={[]} />);

    expect(screen.getByText(/nenhum treino registrado/i)).toBeInTheDocument();
    expect(container.querySelector('.drill-history')).toBeNull();
  });

  it('a data não escorrega um dia por causa de fuso', () => {
    // `new Date('2026-08-16')` é meia-noite UTC, que a oeste de Greenwich cai no dia 15.
    render(<DrillHistory entries={[{ drilledOn: '2026-08-16', recall: 'OK' }]} />);

    expect(screen.getByText('16/08/2026')).toBeInTheDocument();
    expect(screen.queryByText('15/08/2026')).not.toBeInTheDocument();
  });
});
