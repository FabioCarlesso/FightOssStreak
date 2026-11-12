import type { GraduationView } from '@fos/types';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { GraduationCard } from './GraduationCard.tsx';

function renderCard(graduation: GraduationView | null) {
  return render(
    <MemoryRouter>
      <GraduationCard graduation={graduation} />
    </MemoryRouter>,
  );
}

describe('GraduationCard (#193)', () => {
  it('sem meta configurada não aparece', () => {
    const { container } = renderCard({});
    expect(container).toBeEmptyDOMElement();
  });

  it('mostra aulas feitas contra os mínimos, e quanto falta', () => {
    renderCard({
      goal: {
        lastGraduationOn: '2026-08-01',
        currentLabel: 'branca 1º grau',
        nextLabel: 'branca 2º grau',
        minTotal: 80,
        minGi: 60,
        minNogi: 20,
      },
      classes: { total: 23, gi: 18, nogi: 3, unspecified: 2 },
    });

    expect(
      screen.getByRole('heading', { level: 2, name: /próximo grau: branca 2º grau/i }),
    ).toBeInTheDocument();
    expect(screen.getByText(/desde 01\/08\/2026/i)).toBeInTheDocument();
    expect(screen.getByText(/faltam 57/i)).toBeInTheDocument();
    expect(screen.getByText(/faltam 42/i)).toBeInTheDocument();
    expect(screen.getByText(/faltam 17/i)).toBeInTheDocument();
    // Aula sem marcação conta só no total — e o card diz isso, em vez de o número não fechar.
    expect(screen.getByText(/2 aulas sem dizer/i)).toBeInTheDocument();
    // Mínimo de aulas não é graduação.
    expect(screen.getByText(/quem decide o grau é o professor/i)).toBeInTheDocument();
  });

  it('linha sem mínimo e sem aula não aparece, e mínimo batido não vira "faltam 0"', () => {
    renderCard({
      goal: { lastGraduationOn: '2026-08-01', minTotal: 10 },
      classes: { total: 12, gi: 0, nogi: 0, unspecified: 12 },
    });

    expect(screen.queryByText(/com kimono/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/faltam/i)).not.toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
  });
});
