import { useState } from 'react';
import type { ExtraVideoView } from '@fos/types';

/**
 * A faixa de clipes complementares de um nó — "No tatame" (D32).
 *
 * O título nomeia a procedência do material (clipes da própria academia) e é o que separa esta
 * seção do vídeo de referência logo acima: a hierarquia que a D1 pede fica dita na tela — o
 * canônico ensina, o clipe lembra.
 *
 * **Miniatura em vez de player.** A tira mostra o que existe sem montar um `<iframe>` por clipe —
 * cada player do YouTube puxa cerca de um megabyte de JavaScript, e três deles numa tela de revisão
 * é o que transforma o nó em catálogo. O bloco recolhido (`<details>`) resolveria o peso, mas mata
 * o recurso por outro caminho: ninguém expande um acordeão para descobrir se vale ver nove
 * segundos.
 *
 * Um player por vez, de propósito: o clique num segundo clipe desmonta o primeiro. Além de leve, é
 * o que evita dois áudios ao mesmo tempo.
 */
export function ExtraVideos({ videos }: { videos?: ExtraVideoView[] }) {
  const [tocando, setTocando] = useState<string | null>(null);

  if (!videos || videos.length === 0) return null;

  return (
    <section className="card">
      <h3>No tatame</h3>
      <p className="clips__intro">Vídeos complementares</p>

      <ul className="clips">
        {videos.map((video) => {
          const id = video.youtubeId ?? '';
          const vertical = video.orientation === 'VERTICAL';

          return (
            <li key={id} className={`clip ${vertical ? 'clip--vertical' : 'clip--horizontal'}`}>
              <div className="clip__frame">
                {tocando === id ? (
                  <iframe
                    src={video.embedUrl}
                    title={video.title ?? 'Clipe no tatame'}
                    /* `autoplay` no allow porque a url do embed já pede autoplay: o iframe só
                       nasce depois do clique, então é o clique que dá play. */
                    allow="autoplay; accelerometer; encrypted-media; gyroscope; picture-in-picture"
                    allowFullScreen
                    referrerPolicy="strict-origin-when-cross-origin"
                  />
                ) : (
                  <button
                    type="button"
                    className="clip__play"
                    onClick={() => setTocando(id)}
                    aria-label={`Tocar: ${video.title ?? 'clipe no tatame'}`}
                  >
                    <img src={video.thumbnailUrl} alt="" loading="lazy" />
                    <span className="clip__badge" aria-hidden="true">
                      ▶
                    </span>
                  </button>
                )}
              </div>

              {/*
               * Título e crédito em elementos separados porque só o título é recortado. O título
               * é taquigrafia de aula e chega a quatro linhas, o que desalinharia a tira; o
               * crédito ao canal não pode ser recortado nunca, porque é exigência de D7 e não
               * ornamento. O texto inteiro continua no `title` do elemento.
               */}
              <p className="clip__title" title={video.title ?? undefined}>
                {video.note ? <strong>{video.note}</strong> : video.title}
              </p>
              <p className="clip__credit">
                {video.channel && <>canal {video.channel}</>}
                {video.watchUrl && (
                  <>
                    {' · '}
                    <a href={video.watchUrl} target="_blank" rel="noreferrer noopener">
                      no YouTube
                    </a>
                  </>
                )}
              </p>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
