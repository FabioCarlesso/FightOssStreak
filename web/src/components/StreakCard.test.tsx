import type { StreakView } from '@fos/types';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
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

/**
 * Imagem do marco (#106). A composição é testada em `imagemDoMarco.test.ts`; aqui, que o botão só
 * existe no dia do marco e que o clique entrega um PNG para download, sem requisição nenhuma.
 *
 * O jsdom não desenha canvas nem cria `blob:`: os dois são trocados por dublês que registram o que
 * a página pediu.
 */
describe('imagem do marco na home', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function dublesDoNavegador() {
    const textos: string[] = [];
    const ctx = {
      fillRect: vi.fn(),
      fillText: vi.fn((texto: string) => textos.push(texto)),
    };
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(ctx as never);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation((cb, type) => {
      cb(new Blob(['png'], { type }));
    });
    const createObjectURL = vi.fn((blob: Blob) => (blob ? 'blob:fos/imagem' : ''));
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL }));
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);
    const baixados: { href: string; download: string }[] = [];
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement,
    ) {
      baixados.push({ href: this.href, download: this.download });
    });
    return { textos, createObjectURL, fetchSpy, baixados };
  }

  it('no dia do marco, o botão baixa um PNG com o streak, sem chamar rede', async () => {
    const { textos, createObjectURL, fetchSpy, baixados } = dublesDoNavegador();
    render(<StreakCard streak={{ ...BASE, currentStreak: 30, longestStreak: 30 }} />);

    await userEvent.click(screen.getByRole('button', { name: 'Baixar imagem' }));

    expect(baixados).toEqual([{ href: 'blob:fos/imagem', download: 'fos-marco-30-dias.png' }]);
    expect(createObjectURL.mock.calls[0]?.[0].type).toBe('image/png');
    expect(textos).toContain('30');
    expect(textos).toContain('FightOssStreak');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('fora do dia do marco não oferece imagem', () => {
    render(<StreakCard streak={{ ...BASE, currentStreak: 8, longestStreak: 8 }} />);

    expect(screen.queryByRole('button', { name: /baixar imagem/i })).not.toBeInTheDocument();
  });

  it('com 7 dias mas sem treino hoje também não oferece', () => {
    render(<StreakCard streak={{ ...BASE, currentStreak: 7, drilledToday: false }} />);

    expect(screen.queryByRole('button', { name: /baixar imagem/i })).not.toBeInTheDocument();
  });

  it('sem canvas no navegador, avisa em vez de falhar calado', async () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    render(<StreakCard streak={{ ...BASE, currentStreak: 7, longestStreak: 7 }} />);

    await userEvent.click(screen.getByRole('button', { name: 'Baixar imagem' }));

    expect(
      await screen.findByText('Não foi possível gerar a imagem neste navegador.'),
    ).toBeInTheDocument();
  });
});
