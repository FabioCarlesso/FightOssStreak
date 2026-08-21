package dev.fos.web;

import dev.fos.service.AccessRateLimiter;
import dev.fos.service.EmailAccessService;
import dev.fos.service.EmailAuthenticationToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Acesso para quem não tem provedor externo (#52).
 *
 * <p>São as únicas rotas de escrita que qualquer um alcança sem sessão, e uma delas dispara e-mail
 * — daí o freio em todas e a resposta indistinta em {@code /entrar}.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Acesso por e-mail", description = "Pedido de acesso e entrada por link")
public class EmailAccessController {

    /** Poucos por janela: pedir acesso é ação rara, e o excesso só serve para encher a fila. */
    private static final int MAX_POR_JANELA = 5;

    private static final Duration JANELA = Duration.ofHours(1);

    /** Para onde o link leva quando dá certo. É a primeira tela do app. */
    private static final String APOS_ENTRAR = "/hoje";

    /** E quando não dá: a tela de login sabe ler este motivo e explicar. */
    private static final String LINK_INVALIDO = "/hoje?erro=link_invalido";

    private final EmailAccessService acesso;
    private final AccessRateLimiter freio;
    private final Clock clock;
    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public EmailAccessController(EmailAccessService acesso, AccessRateLimiter freio, Clock clock) {
        this.acesso = acesso;
        this.freio = freio;
        this.clock = clock;
    }

    public record EmailRequest(@NotBlank @Email String email) {}

    @PostMapping("/auth/email/solicitar")
    @Operation(
            summary = "Registra um pedido de acesso",
            description =
                    "A conta nasce pendente e entra na fila do dono. Nenhum e-mail sai aqui: o dono"
                            + " recebe o resumo da fila na próxima janela do dia (10h às 22h), e"
                            + " quem pediu só recebe algo quando for aprovado.")
    public ResponseEntity<Void> solicitar(
            @Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        if (!freiar(request, body.email())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        acesso.requestAccess(body.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/auth/email/entrar")
    @Operation(
            summary = "Pede o link de entrada",
            description =
                    "Responde igual para endereço inexistente, pendente, recusado e aprovado — do"
                            + " contrário viraria consulta de quem tem conta no app.")
    public ResponseEntity<Void> entrar(
            @Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        if (!freiar(request, body.email())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        acesso.requestLogin(body.email(), baseUrl());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/login/email/{token}")
    @Operation(summary = "Consome o link e abre a sessão")
    public void consumir(
            @PathVariable String token, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String email = acesso.consume(token).orElse(null);
        if (email == null) {
            response.sendRedirect(LINK_INVALIDO);
            return;
        }
        EmailAuthenticationToken authentication = new EmailAuthenticationToken(email);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // Sem gravar no repositório a sessão morre com a requisição, e o link levaria de volta
        // para a tela de login — o mesmo sintoma da #51, por outro caminho.
        contextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
        response.sendRedirect(APOS_ENTRAR);
    }

    private boolean freiar(HttpServletRequest request, String email) {
        Instant agora = Instant.now(clock);
        freio.evictOlderThan(JANELA, agora);
        return freio.tryAcquire("ip:" + request.getRemoteAddr(), MAX_POR_JANELA, JANELA, agora)
                && freio.tryAcquire(
                        "email:" + EmailAccessService.normalize(email),
                        MAX_POR_JANELA,
                        JANELA,
                        agora);
    }

    /** A URL pública que o browser usou — a mesma que monta o redirect do OAuth. */
    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
