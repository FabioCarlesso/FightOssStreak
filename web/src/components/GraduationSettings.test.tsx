import type { GraduationGoalRequest, GraduationView } from '@fos/types';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { GraduationSettings } from './GraduationSettings.tsx';

const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getGraduation: vi.fn<() => Promise<GraduationView>>(),
    saveGraduationGoal: vi.fn<(goal: GraduationGoalRequest) => Promise<GraduationView>>(),
    deleteGraduationGoal: vi.fn<() => Promise<void>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

beforeEach(() => {
  vi.clearAllMocks();
  apiMock.saveGraduationGoal.mockResolvedValue({});
  apiMock.deleteGraduationGoal.mockResolvedValue(undefined);
});

describe('GraduationSettings (#193)', () => {
  it('sem meta, grava só a data e os mínimos preenchidos — vazio não vira zero', async () => {
    apiMock.getGraduation.mockResolvedValue({});
    render(<GraduationSettings />);

    const data = await screen.findByLabelText(/data do último grau/i);
    expect(screen.queryByRole('button', { name: /remover meta/i })).not.toBeInTheDocument();

    await userEvent.type(data, '2026-08-01');
    await userEvent.type(screen.getByLabelText(/mínimo de aulas/i), '40');
    await userEvent.click(screen.getByRole('button', { name: /salvar/i }));

    expect(apiMock.saveGraduationGoal).toHaveBeenCalledWith({
      lastGraduationOn: '2026-08-01',
      currentLabel: undefined,
      nextLabel: undefined,
      minTotal: 40,
      minGi: undefined,
      minNogi: undefined,
    });
    expect(await screen.findByText(/meta salva/i)).toBeInTheDocument();
  });

  it('com meta, o formulário vem preenchido e dá para remover', async () => {
    apiMock.getGraduation.mockResolvedValue({
      goal: { lastGraduationOn: '2026-08-01', nextLabel: 'branca 2º grau', minTotal: 80 },
      classes: { total: 3, gi: 2, nogi: 1, unspecified: 0 },
    });
    render(<GraduationSettings />);

    expect(await screen.findByDisplayValue('branca 2º grau')).toBeInTheDocument();
    expect(screen.getByDisplayValue('80')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /remover meta/i }));
    expect(apiMock.deleteGraduationGoal).toHaveBeenCalledTimes(1);
  });
});
