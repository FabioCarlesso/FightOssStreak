package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * Vídeo complementar de um nó: material que ajuda a lembrar a técnica, sem ser a referência dela.
 *
 * <p>É um tipo separado de {@link VideoRef}, e não o mesmo embeddable reaproveitado, porque os dois
 * respondem a perguntas diferentes. O canônico responde "qual vídeo ensina este nó" e é único por
 * nó; o complementar responde "o que mais me faz lembrar disto" e é lista. Daí os dois campos que
 * só existem aqui: {@code orientation}, porque clipe de celular é vertical, e {@code note}, que é a
 * anotação de quem catalogou — o único campo do bloco que não vem do YouTube.
 *
 * <p>A hierarquia é de produto, não de banco: o canônico ensina, o complementar lembra (D1/D32).
 */
@Embeddable
public class ExtraVideoRef {

    @Column(name = "youtube_id", nullable = false)
    private String youtubeId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String channel;

    @Column(name = "start_seconds")
    private Integer startSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoOrientation orientation = VideoOrientation.HORIZONTAL;

    @Column private String note;

    protected ExtraVideoRef() {
        // JPA
    }

    public ExtraVideoRef(
            String youtubeId,
            String title,
            String channel,
            Integer startSeconds,
            VideoOrientation orientation,
            String note) {
        this.youtubeId = youtubeId;
        this.title = title;
        this.channel = channel;
        this.startSeconds = startSeconds;
        this.orientation = orientation == null ? VideoOrientation.HORIZONTAL : orientation;
        this.note = note;
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

    public VideoOrientation getOrientation() {
        return orientation;
    }

    public String getNote() {
        return note;
    }
}
