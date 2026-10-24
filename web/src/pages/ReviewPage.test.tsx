import type { DrillRequest, DrillResult, NodeDetail, ReviewAgenda, StreakView } from '@fos/types';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DemoModeProvider } from '../state/DemoModeProvider.tsx';
import { ReviewPage } from './ReviewPage.tsx';

/**
 * A sessão guiada é o percurso que o produto promete — a agenda do SRS seguida um nó por vez —, e
 * por isso entra na lista curta de fluxos de UI testados (D29). O cliente de API é mockado no
 * módulo: nenhum teste toca a rede.
 */
const { apiMock } = vi.hoisted(() => ({
  apiMock: {
    getReviewsToday: vi.fn<() => Promise<ReviewAgenda>>(),
    getNode: vi.fn<(code: string) => Promise<NodeDetail>>(),
    logDrill: vi.fn<(code: string, drill: DrillRequest) => Promise<DrillResult>>(),
    getStreak: vi.fn<() => Promise<StreakView>>(),
  },
}));

vi.mock('../api/client.ts', async () => {
  const real = await vi.importActual<typeof import('@fos/api-client')>('@fos/api-client');
  return { api: apiMock, ApiError: real.ApiError };
});

const CHAVE_DEMO = 'fos.demo';

let rolarAoTopo: ReturnType<typeof vi.spyOn>;

/** Já na ordem do servidor — do mais atrasado para o menos. A tela não reordena. */
const AGENDA: ReviewAgenda = {
  today: '2026-10-24',
  dueCount: 3,
  due: [
    { nodeCode: 'M0.2', title: 'Quedas seguras', daysOverdue: 16 },
    { nodeCode: 'M0.1', title: 'Etiqueta de tatame', daysOverdue: 3 },
    { nodeCode: 'M0.3', title: 'Como tocar (tap)', daysOverdue: 0 },
  ],
};

function no(code: string, title: string): NodeDetail {
  return {
    code,
    title,
    moduleCode: 'M0',
    moduleTitle: 'Fundamentos',
    belt: 'BRANCA',
    status: 'COMPLETED',
    concept: `Conceito de ${code}.`,
    safetyNotice: 'Pratique somente em academia.',
    video: {
      catalogued: true,
      youtubeId: `yt-${code}`,
      embedUrl: `https://www.youtube-nocookie.com/embed/yt-${code}`,
      title: `Vídeo de ${code}`,
      channel: 'Canal',
    },
    srs: { scheduled: true, nextReviewOn: '2026-10-08', repetitions: 2, intervalDays: 6 },
  };
}

const NOS: Record<string, NodeDetail> = {
  'M0.1': no('M0.1', 'Etiqueta de tatame'),
  'M0.2': no('M0.2', 'Quedas seguras'),
  'M0.3': no('M0.3', 'Como tocar (tap)'),
};

function resultado(code: string): DrillResult {
  return {
    nodeCode: code,
    drilledOn: '2026-10-24',
    status: 'COMPLETED',
    nextReviewOn: `2026-11-0${Object.keys(NOS).indexOf(code) + 1}`,
    intervalDays: 10,
    streak: { currentStreak: 4 },
  };
}

function renderReview() {
  return render(
    <DemoModeProvider>
      <MemoryRouter initialEntries={['/revisar']}>
        <Routes>
          <Route path="/revisar" element={<ReviewPage />} />
          <Route path="/hoje" element={<p>tela de hoje</p>} />
        </Routes>
      </MemoryRouter>
    </DemoModeProvider>,
  );
}

beforeEach(() => {
  sessionStorage.clear();
  apiMock.getReviewsToday.mockReset().mockResolvedValue(AGENDA);
  apiMock.getNode.mockReset().mockImplementation((code) => Promise.resolve(NOS[code]!));
  apiMock.logDrill.mockReset().mockImplementation((code) => Promise.resolve(resultado(code)));
  apiMock.getStreak.mockReset().mockResolvedValue({ currentStreak: 4 });
  // O jsdom não rola a página; o espião só confere que o avanço pede para voltar ao topo.
  rolarAoTopo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined);
});

afterEach(() => {
  sessionStorage.clear();
  vi.restoreAllMocks();
});

async function registrar() {
  await userEvent.click(await screen.findByRole('button', { name: /treinei isso hoje/i }));
}

describe('sessão guiada de revisão', () => {
  it('abre no mais atrasado, na ordem do servidor, com "1 de N"', async () => {
    renderReview();

    expect(await screen.findByText('1 de 3')).toBeInTheDocument();
    expect(await screen.findByText('Conceito de M0.2.')).toBeInTheDocument();
    expect(screen.getByText('16 dias atrasado')).toBeInTheDocument();
  });

  it('registrar avança para o próximo nó, grava no código certo e confirma a próxima revisão', async () => {
    renderReview();
    await screen.findByText('Conceito de M0.2.');

    await registrar();

    expect(apiMock.logDrill).toHaveBeenCalledWith('M0.2', { recall: 'OK', note: undefined });
    expect(await screen.findByText('2 de 3')).toBeInTheDocument();
    expect(await screen.findByText('Conceito de M0.1.')).toBeInTheDocument();
    expect(screen.getByText(/M0\.2 registrado/)).toHaveTextContent('2026-11-02');

    // O formulário do passo 2 é outro: grava em M0.1, não no nó que acabou de sair (D31).
    await registrar();
    expect(apiMock.logDrill).toHaveBeenLastCalledWith('M0.1', { recall: 'OK', note: undefined });
  });

  it('a fila é capturada uma vez: registrar não encolhe o total nem relê a agenda', async () => {
    renderReview();
    await screen.findByText('Conceito de M0.2.');

    await registrar();
    await screen.findByText('2 de 3');

    expect(apiMock.getReviewsToday).toHaveBeenCalledTimes(1);
    expect(screen.queryByText(/de 2$/)).not.toBeInTheDocument();
  });

  it('pular avança sem gravar nada', async () => {
    renderReview();
    await screen.findByText('Conceito de M0.2.');

    await userEvent.click(screen.getByRole('button', { name: /pular este nó/i }));

    expect(await screen.findByText('2 de 3')).toBeInTheDocument();
    expect(screen.getByText(/M0\.2 pulado/)).toBeInTheDocument();
    expect(apiMock.logDrill).not.toHaveBeenCalled();
  });

  it('avançar volta ao topo, onde estão o progresso e a confirmação', async () => {
    renderReview();
    await screen.findByText('Conceito de M0.2.');

    await registrar();
    await screen.findByText('2 de 3');
    expect(rolarAoTopo).toHaveBeenCalledTimes(1);

    await userEvent.click(screen.getByRole('button', { name: /pular este nó/i }));
    await screen.findByText('3 de 3');
    expect(rolarAoTopo).toHaveBeenCalledTimes(2);
  });

  it('o último passo leva à tela de fim, com revisados, pulados e o streak atualizado', async () => {
    renderReview();
    await screen.findByText('Conceito de M0.2.');

    await registrar();
    await screen.findByText('Conceito de M0.1.');
    await userEvent.click(screen.getByRole('button', { name: /pular este nó/i }));
    await screen.findByText('Conceito de M0.3.');
    await registrar();

    expect(await screen.findByRole('heading', { name: /revisão concluída/i })).toBeInTheDocument();
    expect(screen.getByText(/revisados/)).toHaveTextContent('2 revisados · 1 pulado');
    expect(await screen.findByText(/streak/i)).toHaveTextContent('4 dias seguidos');
    expect(screen.getByRole('link', { name: /voltar para hoje/i })).toBeInTheDocument();
  });

  it('só o passo atual monta player; o próximo nó é adiantado só como dado', async () => {
    const { container } = renderReview();
    await screen.findByText('Conceito de M0.2.');

    await waitFor(() => expect(apiMock.getNode).toHaveBeenCalledWith('M0.1'));
    const players = container.querySelectorAll('iframe');
    expect(players).toHaveLength(1);
    expect(players[0]!.getAttribute('src')).toContain('yt-M0.2');

    // O passo seguinte usa o pedido adiantado, não faz outro.
    await registrar();
    await screen.findByText('Conceito de M0.1.');
    expect(apiMock.getNode.mock.calls.filter(([code]) => code === 'M0.1')).toHaveLength(1);
  });

  it('sem nada vencido, mostra estado vazio com caminho de volta', async () => {
    apiMock.getReviewsToday.mockResolvedValue({ today: '2026-10-24', dueCount: 0, due: [] });
    renderReview();

    expect(await screen.findByText(/nada vencido hoje/i)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /voltar para hoje/i })).toBeInTheDocument();
    expect(screen.queryByText(/de 0/)).not.toBeInTheDocument();
  });

  it('em modo demonstração a sessão não abre e nada é pedido ao servidor', async () => {
    sessionStorage.setItem(CHAVE_DEMO, 'on');
    renderReview();

    expect(await screen.findByText(/modo demonstração não grava/i)).toBeInTheDocument();
    expect(apiMock.getReviewsToday).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: /treinei isso hoje/i })).not.toBeInTheDocument();
  });
});
