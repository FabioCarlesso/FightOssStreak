package dev.fos.web;

import dev.fos.service.CurrentUserProvider;
import dev.fos.service.FeedbackService;
import dev.fos.web.dto.FeedbackDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O que só a administração do app vê.
 *
 * <p>Aqui morava também a fila de solicitações de acesso, que saiu inteira com o portão de
 * aprovação (D48). Sobra a fila de feedback — e o desenho continua o mesmo: quem pode chamar isto é
 * decidido <b>fora</b> do controller, pelo {@link dev.fos.config.OwnerOnlyInterceptor} registrado
 * sobre {@code /api/admin/**}. Nenhum método daqui pergunta quem é o chamador; se algum
 * perguntasse, haveria dois lugares decidindo a mesma coisa e um dia eles discordariam.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administração", description = "Fila de feedback; restrita à conta de administração")
public class AdminController {

    private final CurrentUserProvider currentUser;
    private final FeedbackService feedbackService;

    public AdminController(CurrentUserProvider currentUser, FeedbackService feedbackService) {
        this.currentUser = currentUser;
        this.feedbackService = feedbackService;
    }

    @GetMapping("/feedback")
    @Operation(summary = "Fila de feedback, da mais antiga para a mais nova")
    public FeedbackDtos.FeedbackList feedback() {
        return feedbackService.queue();
    }

    @PostMapping("/feedback/{id}/status")
    @Operation(summary = "Muda o status de um feedback (em análise, resolvido, recusado)")
    public FeedbackDtos.FeedbackView decideFeedback(
            @PathVariable Long id, @Valid @RequestBody FeedbackDtos.FeedbackStatusRequest request) {
        return feedbackService.decide(id, request.status(), currentUser.currentUserId());
    }
}
