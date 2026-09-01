package dev.fos.web;

import dev.fos.model.AccessStatus;
import dev.fos.model.Role;
import dev.fos.service.AdminUserService;
import dev.fos.service.CurrentUserProvider;
import dev.fos.service.FeedbackService;
import dev.fos.service.SiteHealthService;
import dev.fos.service.UsagePanelService;
import dev.fos.web.dto.AdminHealthDtos;
import dev.fos.web.dto.AdminPanelDtos;
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
        description =
                "Painel de uso, contas do sistema e fila de feedback; restrita a quem administra")
public class AdminController {

    private final CurrentUserProvider currentUser;
    private final FeedbackService feedbackService;
    private final AdminUserService adminUsers;
    private final UsagePanelService painelDeUso;
    private final SiteHealthService saudeDoSite;

    public AdminController(
            CurrentUserProvider currentUser,
            FeedbackService feedbackService,
            AdminUserService adminUsers,
            UsagePanelService painelDeUso,
            SiteHealthService saudeDoSite) {
        this.currentUser = currentUser;
        this.feedbackService = feedbackService;
        this.adminUsers = adminUsers;
        this.painelDeUso = painelDeUso;
        this.saudeDoSite = saudeDoSite;
    }

    @GetMapping("/painel")
    @Operation(
            summary = "Acessos, funil, origem e perfil de uso do app",
            description =
                    "Agregado e de ninguém: nenhum campo desta resposta identifica uma pessoa —"
                            + " não há e-mail, nome nem id de conta. Lê apenas a contagem diária"
                            + " (`usage_daily`), nunca a tabela crua de eventos"
                            + " (docs/11-privacidade.md). O período termina ontem: hoje ainda"
                            + " recebe evento e não foi fechado.")
    public AdminPanelDtos.PanelView painel(
            @Parameter(description = "Tamanho do período: 7, 30 ou 90 dias")
                    @RequestParam(defaultValue = "7")
                    int dias) {
        return painelDeUso.painel(dias);
    }

    @GetMapping("/saude")
    @Operation(
            summary = "Requisições, erro e latência do próprio app",
            description =
                    "Agregado como o painel de uso, e pela mesma razão: nenhum campo identifica"
                            + " pessoa — não há e-mail, nome, id de conta nem endereço. A rota é"
                            + " sempre o *padrão* casado pelo roteamento, nunca um caminho com"
                            + " segmento preenchido. Inclui a hora corrente, ao contrário do"
                            + " painel: aqui a leitura é operacional. Não responde se o site"
                            + " ficou fora do ar — app parado não escreve estatística; quem"
                            + " responde isso é a verificação externa em cron.")
    public AdminHealthDtos.HealthView saude(
            @Parameter(description = "Tamanho do período em horas: 24, 72 ou 168")
                    @RequestParam(defaultValue = "24")
                    int horas) {
        return saudeDoSite.saude(horas);
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
                    "Bloquear vale na ação seguinte da conta, inclusive numa aba já aberta: o"
                            + " portão relê o estado a cada requisição. 409 para si mesmo e para a"
                            + " última conta de administração.")
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
