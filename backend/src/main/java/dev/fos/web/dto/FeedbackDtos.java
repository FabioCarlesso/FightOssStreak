package dev.fos.web.dto;

import dev.fos.model.FeedbackCategory;
import dev.fos.model.FeedbackStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Feedback de usuário: bug, conteúdo errado, troca de vídeo, sugestão
 * (docs/13-feedback-usuarios.md).
 */
public final class FeedbackDtos {

    private FeedbackDtos() {}

    /**
     * @param nodeCode nó referenciado, opcional — nem todo feedback é sobre um nó do currículo
     */
    public record FeedbackRequest(
            @NotNull FeedbackCategory category,
            String nodeCode,
            @NotBlank @Size(max = 2000) String message) {}

    /**
     * @param authorLabel quem mandou — só aparece na fila do dono
     * @param nodeCode nó referenciado, ou {@code null}
     */
    public record FeedbackView(
            Long id,
            FeedbackCategory category,
            String nodeCode,
            String message,
            FeedbackStatus status,
            Instant createdAt,
            Instant decidedAt,
            String authorLabel) {}

    public record FeedbackList(List<FeedbackView> items) {}

    public record FeedbackStatusRequest(@NotNull FeedbackStatus status) {}
}
