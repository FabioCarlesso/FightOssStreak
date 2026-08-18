import type { ExtraVideoView } from '@fos/types';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ExtraVideos } from './ExtraVideos.tsx';

/**
 * O que estes testes protegem é a promessa que justifica a tira existir (D32): mostrar os clipes
 * disponíveis **sem** montar um player para cada um. Cada `<iframe>` do YouTube puxa cerca de um
 * megabyte de JavaScript, então "carrega só no clique" não é otimização — é a diferença entre a
 * tela de revisão continuar leve e virar catálogo.
 */
const clipe = (youtubeId: string, resto: Partial<ExtraVideoView> = {}): ExtraVideoView => ({
  youtubeId,
  title: `título de ${youtubeId}`,
  channel: 'Guiabasicodejiujitsu',
  orientation: 'VERTICAL',
  embedUrl: `https://www.youtube-nocookie.com/embed/${youtubeId}?autoplay=1&loop=1&playlist=${youtubeId}`,
  watchUrl: `https://www.youtube.com/watch?v=${youtubeId}`,
  thumbnailUrl: `https://i.ytimg.com/vi/${youtubeId}/hqdefault.jpg`,
  ...resto,
});

const iframes = (container: HTMLElement) => container.querySelectorAll('iframe');

/** O único iframe montado, falhando alto se houver zero ou mais de um. */
const unicoIframe = (container: HTMLElement) => {
  const encontrados = iframes(container);
  expect(encontrados).toHaveLength(1);
  return encontrados[0]!;
};

describe('tira de clipes do tatame', () => {
  it('não monta nenhum iframe antes do clique', () => {
    const { container } = render(
      <ExtraVideos videos={[clipe('AkQxiCrsGo4'), clipe('9wEhZ1PdoMI')]} />,
    );

    expect(iframes(container)).toHaveLength(0);
    expect(screen.getAllByRole('button', { name: /tocar:/i })).toHaveLength(2);
  });

  it('monta o player só do clipe clicado, com autoplay e loop na url', async () => {
    const { container } = render(
      <ExtraVideos videos={[clipe('AkQxiCrsGo4'), clipe('9wEhZ1PdoMI')]} />,
    );

    await userEvent.click(screen.getByRole('button', { name: /título de AkQxiCrsGo4/i }));

    expect(unicoIframe(container).getAttribute('src')).toContain(
      'autoplay=1&loop=1&playlist=AkQxiCrsGo4',
    );
  });

  /** Um player por vez: além de leve, é o que evita dois áudios tocando ao mesmo tempo. */
  it('troca de player em vez de acumular quando outro clipe é clicado', async () => {
    const { container } = render(
      <ExtraVideos videos={[clipe('AkQxiCrsGo4'), clipe('9wEhZ1PdoMI')]} />,
    );

    await userEvent.click(screen.getByRole('button', { name: /título de AkQxiCrsGo4/i }));
    await userEvent.click(screen.getByRole('button', { name: /título de 9wEhZ1PdoMI/i }));

    expect(unicoIframe(container).getAttribute('src')).toContain('playlist=9wEhZ1PdoMI');
  });

  /** A seção se separa do vídeo de referência pelo título — é o que diz a procedência do material. */
  it('anuncia a seção como os clipes do tatame, distinta do vídeo de referência', () => {
    render(<ExtraVideos videos={[clipe('AkQxiCrsGo4')]} />);

    expect(screen.getByRole('heading', { name: 'No tatame' })).toBeInTheDocument();
    expect(screen.getByText('Vídeos complementares')).toBeInTheDocument();
  });

  /** Crédito visível ao canal é política de uso de vídeo (D7), não metadado opcional. */
  it('credita o canal em cada clipe', () => {
    render(
      <ExtraVideos
        videos={[clipe('AkQxiCrsGo4'), clipe('9wEhZ1PdoMI', { channel: 'Outro Canal' })]}
      />,
    );

    expect(screen.getByText(/canal Guiabasicodejiujitsu/)).toBeInTheDocument();
    expect(screen.getByText(/canal Outro Canal/)).toBeInTheDocument();
  });

  it('mostra a anotação do curador no lugar do título quando ela existe', () => {
    render(
      <ExtraVideos videos={[clipe('AkQxiCrsGo4', { note: 'o giro para o lado da cabeça' })]} />,
    );

    expect(screen.getByText('o giro para o lado da cabeça')).toBeInTheDocument();
    expect(screen.queryByText('título de AkQxiCrsGo4')).not.toBeInTheDocument();
  });

  /** Nó sem clipe é o caso de 43 dos 46 nós: a seção não pode aparecer vazia. */
  it('não rende nada quando o nó não tem clipe', () => {
    const { container } = render(<ExtraVideos videos={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});
