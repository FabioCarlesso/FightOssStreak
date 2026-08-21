package dev.fos.web;

import dev.fos.service.DemoAccessService;
import dev.fos.service.DemoAuthenticationToken;
import dev.fos.web.dto.DemoDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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
    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public DemoController(DemoAccessService demo) {
        this.demo = demo;
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
        DemoAccessService.DemoSession sessao = demo.create(request.getRemoteAddr());
        SecurityContextHolder.getContext()
                .setAuthentication(new DemoAuthenticationToken(sessao.subject()));
        // Sem gravar no repositório a sessão morre com a requisição, e o visitante cairia na tela
        // de login logo depois de a demonstração ter sido criada — o sintoma da #51 por outro
        // caminho.
        contextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
        return new DemoDtos.DemoSessionView(DESTINO, sessao.expiresAt());
    }
}
