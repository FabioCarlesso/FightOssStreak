import type { DisclaimerStatus } from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { DisclaimerGate } from './DisclaimerGate.tsx';

/**
 * O portão do disclaimer decide se o app é utilizável. Um defeito aqui ou trava tudo, ou — pior —
 * deixa passar sem aceite, e o aviso é requisito de produto (docs/06-disclaimer-responsabilidade.md).
 *
 * O cliente de API é mockado no módulo: nenhum teste toca a rede.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getDisclaimer: vi.fn<() => Promise<DisclaimerStatus>>(),
    acceptDisclaimer: vi.fn<(version: string) => Promise<DisclaimerStatus>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const CONTEUDO_PROTEGIDO = 'árvore do currículo';

function renderGate() {
  return render(
    <DisclaimerGate>
      <p>{CONTEUDO_PROTEGIDO}</p>
    </DisclaimerGate>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('DisclaimerGate', () => {
  it('sem aceite, bloqueia o app e mostra o aviso', async () => {
    apiMock.getDisclaimer.mockResolvedValue({ accepted: false, currentVersion: '2024-01' });

    renderGate();

    expect(await screen.findByRole('button', { name: /li e concordo/i })).toBeInTheDocument();
    expect(screen.queryByText(CONTEUDO_PROTEGIDO)).not.toBeInTheDocument();
  });

  it('com aceite da versão vigente, libera o app sem mostrar o aviso', async () => {
    apiMock.getDisclaimer.mockResolvedValue({
      accepted: true,
      currentVersion: '2024-01',
      acceptedVersion: '2024-01',
    });

    renderGate();

    expect(await screen.findByText(CONTEUDO_PROTEGIDO)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /li e concordo/i })).not.toBeInTheDocument();
  });

  it('aceite de versão anterior volta a exigir aceite, e da versão nova', async () => {
    // Quando o texto muda materialmente o backend volta a reportar `accepted: false`, mesmo com
    // um aceite antigo registrado. É esse o mecanismo que faz o aviso reaparecer.
    apiMock.getDisclaimer.mockResolvedValue({
      accepted: false,
      currentVersion: '2024-06',
      acceptedVersion: '2024-01',
    });

    renderGate();

    expect(await screen.findByRole('button', { name: /li e concordo/i })).toBeInTheDocument();
    expect(screen.queryByText(CONTEUDO_PROTEGIDO)).not.toBeInTheDocument();
    expect(screen.getByText(/2024-06/)).toBeInTheDocument();
  });

  it('aceitar registra a versão vigente e libera o app', async () => {
    apiMock.getDisclaimer
      .mockResolvedValueOnce({ accepted: false, currentVersion: '2024-06' })
      .mockResolvedValue({
        accepted: true,
        currentVersion: '2024-06',
        acceptedVersion: '2024-06',
      });
    apiMock.acceptDisclaimer.mockResolvedValue({ accepted: true, currentVersion: '2024-06' });

    renderGate();
    await userEvent.click(await screen.findByRole('button', { name: /li e concordo/i }));

    // A versão enviada é a vigente, não a que o usuário aceitou antes: registrar a errada
    // deixaria o aceite inconsistente sem que a tela mostrasse nada de estranho.
    expect(apiMock.acceptDisclaimer).toHaveBeenCalledWith('2024-06');
    expect(await screen.findByText(CONTEUDO_PROTEGIDO)).toBeInTheDocument();
  });

  it('API fora do ar bloqueia o app e oferece nova tentativa', async () => {
    apiMock.getDisclaimer.mockRejectedValueOnce(new Error('Failed to fetch'));

    renderGate();

    expect(await screen.findByText(/não foi possível falar com a API/i)).toBeInTheDocument();
    expect(screen.queryByText(CONTEUDO_PROTEGIDO)).not.toBeInTheDocument();

    // Falhar não pode ser estado terminal: o backend caído é o caso comum em dev.
    apiMock.getDisclaimer.mockResolvedValue({ accepted: true, currentVersion: '2024-06' });
    await userEvent.click(screen.getByRole('button', { name: /tentar de novo/i }));

    expect(await screen.findByText(CONTEUDO_PROTEGIDO)).toBeInTheDocument();
  });

  it('falha ao registrar o aceite mantém o app bloqueado e explica o erro', async () => {
    apiMock.getDisclaimer.mockResolvedValue({ accepted: false, currentVersion: '2024-06' });
    apiMock.acceptDisclaimer.mockRejectedValue(new Error('500 Internal Server Error'));

    renderGate();
    await userEvent.click(await screen.findByRole('button', { name: /li e concordo/i }));

    await waitFor(() => {
      expect(screen.getByText(/500 Internal Server Error/)).toBeInTheDocument();
    });
    expect(screen.queryByText(CONTEUDO_PROTEGIDO)).not.toBeInTheDocument();
  });
});
