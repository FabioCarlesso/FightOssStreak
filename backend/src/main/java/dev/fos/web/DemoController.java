package dev.fos.web;

import dev.fos.model.UsageEventType;
import dev.fos.service.CurrentUserProvider;
import dev.fos.service.DemoAccessService;
import dev.fos.service.DemoAuthenticationToken;
import dev.fos.service.DemoUnavailableException;
import dev.fos.service.SessionLogin;
import dev.fos.service.UsageCollector;
import dev.fos.web.dto.DemoDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O degrau antes do portão: abrir o app já carregado, sem pedir nada a ninguém (#62).
 *
 * <p>Público, como as rotas de acesso por e-mail — e, como elas, com freio: é escrita que qualquer
 * um alcança sem sessão. O que sai daqui é uma sessão de verdade, numa conta de verdade, que grava
 * de verdade. O que a diferencia é ter prazo e não ser de ninguém.
 *
 * <p>Não confundir com o "modo demonstração" da D31, que é inspeção do autor <em>dentro</em> do
 * app, com login feito, e não grava nada. São duas coisas com o mesmo apelido em pontas opostas do
 * portão.
 */
@RestController
@RequestMapping("/api/demo")
@Tag(name = "Demonstração", description = "Sessão de demonstração descartável")
public class DemoController {

    /** Para onde a demonstração abre. É a primeira tela do app. */
    private static final String DESTINO = "/hoje";

    private final DemoAccessService demo;
    private final CurrentUserProvider currentUser;
    private final SessionLogin sessao;
    private final UsageCollector uso;

    public DemoController(
            DemoAccessService demo,
            CurrentUserProvider currentUser,
            SessionLogin sessao,
            UsageCollector uso) {
        this.demo = demo;
        this.currentUser = currentUser;
        this.sessao = sessao;
        this.uso = uso;
    }

    @PostMapping("/sessao")
    @Operation(
            summary = "Abre uma demonstração",
            description =
                    "Cria uma conta descartável, copia o estado da conta-modelo com as datas"
                            + " rebaseadas e abre sessão. A conta é apagada por completo quando o"
                            + " prazo vence.")
    public DemoDtos.DemoSessionView abrir(
            HttpServletRequest request, HttpServletResponse response) {
        // Quem já está logado de verdade não tem a sessão trocada por uma descartável em silêncio.
        // O caminho existe: o rodapé do app leva à apresentação, e lá está o botão. Como a
        // demonstração é cópia da conta-modelo, ela se PARECE com o app de quem clicou — e o que
        // for registrado nela morre no prazo. Demonstração já aberta (ou vencida) segue podendo
        // abrir outra: é o "começar de novo" da tela de fim.
        currentUser
                .findCurrentUser()
                .filter(usuario -> !usuario.isDemo())
                .ifPresent(
                        usuario -> {
                            throw DemoUnavailableException.alreadySignedIn();
                        });

        DemoAccessService.DemoSession demonstracao = demo.create(request.getRemoteAddr());
        // Rotacionar o id, gravar o contexto e anotar a sessão são os três deveres de todo login
        // que a aplicação faz por conta própria — ver SessionLogin.
        this.sessao.signIn(new DemoAuthenticationToken(demonstracao.subject()), request, response);
        // Depois do login: é o que faz o evento sair com a conta descartável em `user_id`, e ela
        // leva os eventos dela junto quando vence (#84, D50).
        uso.funnel(UsageEventType.DEMONSTRACAO_ABERTA);
        return new DemoDtos.DemoSessionView(DESTINO, demonstracao.expiresAt());
    }
}
