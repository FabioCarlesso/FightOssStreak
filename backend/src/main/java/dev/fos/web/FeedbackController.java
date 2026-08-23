package dev.fos.web;

import dev.fos.service.CurrentUserProvider;
import dev.fos.service.FeedbackService;
import dev.fos.web.dto.FeedbackDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feedback de usuário, do lado de quem manda (docs/13-feedback-usuarios.md).
 *
 * <p>A fila do lado de quem decide vive em {@link AdminController}, sob {@code /api/admin/**}.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Feedback", description = "Bug, conteúdo errado, troca de vídeo, sugestão")
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final CurrentUserProvider currentUser;

    public FeedbackController(FeedbackService feedbackService, CurrentUserProvider currentUser) {
        this.feedbackService = feedbackService;
        this.currentUser = currentUser;
    }

    @PostMapping("/feedback")
    @Operation(summary = "Envia um feedback; opcionalmente amarrado a um nó do currículo")
    public FeedbackDtos.FeedbackView submit(
            @Valid @RequestBody FeedbackDtos.FeedbackRequest request) {
        return feedbackService.submit(currentUser.currentUser(), request);
    }
}
