package dev.fos.web;

import dev.fos.model.AccessStatus;
import dev.fos.model.Role;
import dev.fos.service.AdminUserService;
import dev.fos.service.CurrentUserProvider;
import dev.fos.service.FeedbackService;
import dev.fos.web.dto.AdminUserDtos;
import dev.fos.web.dto.FeedbackDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * O que só a administração do app vê.
 *
 * <p>Aqui morava também a fila de solicitações de acesso, que saiu inteira com o portão de
 * aprovação (D48) — e o que voltou no lugar dela, com a #88, é o oposto de uma fila: a lista
 * <em>nominal</em> das contas que já entraram, com bloqueio reativo em vez de aprovação prévia.
 *
 * <p>O desenho continua o mesmo: quem pode chamar isto é decidido <b>fora</b> do controller, pelo
 * {@link dev.fos.config.OwnerOnlyInterceptor} registrado sobre {@code /api/admin/**}. Nenhum método
 * daqui pergunta quem é o chamador; se algum perguntasse, haveria dois lugares decidindo a mesma
 * coisa e um dia eles discordariam. O que o controller passa adiante é <em>quem está agindo</em>,
 * que é outra pergunta — dela dependem as guardas de auto-rebaixamento e de auditoria.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(
        name = "Administração",
        description = "Contas do sistema e fila de feedback; restrita a quem administra")
public class AdminController {

    private final CurrentUserProvider currentUser;
    private final FeedbackService feedbackService;
    private final AdminUserService adminUsers;

    public AdminController(
            CurrentUserProvider currentUser,
            FeedbackService feedbackService,
            AdminUserService adminUsers) {
        this.currentUser = currentUser;
        this.feedbackService = feedbackService;
        this.adminUsers = adminUsers;
    }

    @GetMapping("/usuarios")
    @Operation(
            summary = "Contas do sistema, da mais nova para a mais antiga",
            description =
                    "Paginada, com teto de tamanho. Filtros e busca combinam entre si. Conta de"
                            + " demonstração (D39) não aparece: ela não é de ninguém e vence"
                            + " sozinha.")
    public AdminUserDtos.AdminUserPage usuarios(
            @Parameter(description = "Filtra por estado de acesso") @RequestParam(required = false)
                    AccessStatus status,
            @Parameter(description = "Filtra por papel") @RequestParam(required = false) Role role,
            @Parameter(description = "Só contas com (ou sem) e-mail verificado")
                    @RequestParam(required = false)
                    Boolean verificado,
            @Parameter(description = "Trecho de e-mail ou rótulo") @RequestParam(required = false)
                    String busca,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Teto de 100; acima disso vale o teto")
                    @RequestParam(defaultValue = "20")
                    int size) {
        return adminUsers.list(status, role, verificado, busca, page, size);
    }

    @PostMapping("/usuarios/{id}/role")
    @Operation(
            summary = "Promove a ADMIN ou rebaixa a USUARIO",
            description =
                    "409 quando a conta não confirmou o e-mail, quando alguém tenta rebaixar a si"
                            + " mesmo, e quando a mudança deixaria o app sem administrador.")
    public AdminUserDtos.AdminUserView mudarPapel(
            @PathVariable Long id, @Valid @RequestBody AdminUserDtos.AdminRoleRequest request) {
        return adminUsers.changeRole(currentUser.currentUserId(), id, request.role());
    }

    @PostMapping("/usuarios/{id}/status")
    @Operation(
            summary = "Bloqueia (RECUSADO) ou devolve o acesso (APROVADO)",
            description =
                    "Bloquear derruba as sessões abertas da conta na hora. 409 para si mesmo e para"
                            + " a última conta de administração.")
    public AdminUserDtos.AdminUserView mudarAcesso(
            @PathVariable Long id, @Valid @RequestBody AdminUserDtos.AdminStatusRequest request) {
        return adminUsers.changeStatus(
                currentUser.currentUserId(), id, request.status(), request.motivo());
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
