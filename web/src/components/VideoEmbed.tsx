import type { VideoView } from '@fos/types';

/**
 * Vídeo por embed do player oficial do YouTube (D7).
 *
 * Um nó sem vídeo catalogado é estado normal — a curadoria é incremental e trabalho humano. O
 * componente diz isso explicitamente em vez de mostrar um espaço quebrado.
 *
 * O crédito ao canal fica visível junto do player: além de correto, é o que preserva a relação
 * com os criadores caso o projeto cresça.
 */
export function VideoEmbed({ video, title }: { video?: VideoView; title?: string }) {
  if (!video?.catalogued || !video.embedUrl) {
    return (
      <p className="empty">
        Vídeo ainda não catalogado para este nó. A escolha de um bom vídeo por nó é etapa de
        curadoria manual — ver <code>backend/src/main/resources/curriculum/README.md</code>.
      </p>
    );
  }

  return (
    <figure className="video">
      <div className="video__frame">
        <iframe
          src={video.embedUrl}
          title={video.title ?? title ?? 'Vídeo de referência'}
          allow="accelerometer; encrypted-media; gyroscope; picture-in-picture"
          allowFullScreen
          loading="lazy"
          referrerPolicy="strict-origin-when-cross-origin"
        />
      </div>
      <figcaption>
        {video.title && <strong>{video.title}</strong>}
        {video.channel && <span> — canal {video.channel}</span>}
        {video.watchUrl && (
          <>
            {' · '}
            <a href={video.watchUrl} target="_blank" rel="noreferrer noopener">
              assistir no YouTube
            </a>
          </>
        )}
      </figcaption>
    </figure>
  );
}
