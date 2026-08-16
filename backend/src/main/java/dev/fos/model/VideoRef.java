package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Referência a um vídeo do YouTube, sempre por embed do player oficial (D7).
 *
 * <p>O canal é obrigatório junto com o id porque crédito visível ao criador é parte da política
 * de uso de vídeo — não é opcional nem cosmético.
 */
@Embeddable
public class VideoRef {

    @Column(name = "video_youtube_id")
    private String youtubeId;

    @Column(name = "video_title")
    private String title;

    @Column(name = "video_channel")
    private String channel;

    @Column(name = "video_start_seconds")
    private Integer startSeconds;

    protected VideoRef() {
        // JPA
    }

    public VideoRef(String youtubeId, String title, String channel, Integer startSeconds) {
        this.youtubeId = youtubeId;
        this.title = title;
        this.channel = channel;
        this.startSeconds = startSeconds;
    }

    public String getYoutubeId() {
        return youtubeId;
    }

    public String getTitle() {
        return title;
    }

    public String getChannel() {
        return channel;
    }

    public Integer getStartSeconds() {
        return startSeconds;
    }

    /** Um nó sem vídeo catalogado é estado normal, não erro — a curadoria é incremental. */
    public boolean isCatalogued() {
        return youtubeId != null && !youtubeId.isBlank();
    }
}
