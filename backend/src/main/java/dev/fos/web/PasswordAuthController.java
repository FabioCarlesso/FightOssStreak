package dev.fos.web;

import dev.fos.service.AccessRateLimiter;
import dev.fos.service.EmailAccessService;
import dev.fos.service.PasswordAccessService;
import dev.fos.service.PasswordAuthenticationToken;
import dev.fos.service.SessionLogin;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Cadastro, entrada e recuperação com senha própria (#81, D47).
 *
 * <p>Todas as rotas são públicas — é a porta de entrada de quem ainda não tem conta — e todas têm
 * freio, porque quatro delas disparam e-mail e uma confere credencial. As três que respondem {@code
 * 202} respondem <b>igual</b> para endereço que existe e que não existe: a razão da D37, agora
 * valendo em triplicata.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Cadastro e senha", description = "Conta com e-mail e senha, confirmada por link")
public class PasswordAuthController {

    /**
     * Poucos por janela: cadastrar, reenviar e recuperar são ações raras, e todas mandam e-mail.
     */
    private static final int MAX_EMAILS_POR_JANELA = 5;

    private static final Duration JANELA_EMAILS = Duration.ofHours(1);

    /** Para onde a confirmação leva quando dá certo. É a primeira tela do app. */
    private static final String APOS_CONFIRMAR = "/hoje";

    /** E quando não dá: a tela sabe ler este motivo e oferecer outro link. */
    private static final String LINK_INVALIDO = "/entrar?erro=confirmacao_invalida";

    private final PasswordAccessService senha;
    private final SessionLogin sessao;
    private final AccessRateLimiter freio;
    private final Clock clock;

    public PasswordAuthController(
            PasswordAccessService senha,
            SessionLogin sessao,
            AccessRateLimiter freio,
            Clock clock) {
        this.senha = senha;
        this.sessao = sessao;
        this.freio = freio;
        this.clock = clock;
    }

    public record CadastroRequest(@NotBlank @Email String email, @NotBlank String senha) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String senha) {}

    public record EmailRequest(@NotBlank @Email String email) {}

    public record SenhaRequest(@NotBlank String senha) {}

    /**
     * @param valido se o link de redefinição ainda serve; a tela decide entre formulário e aviso
     */
    public record LinkView(boolean valido) {}

    @PostMapping("/cadastro")
    @Operation(
            summary = "Cria a conta e manda o link de confirmação",
            description =
                    "Não abre sessão: a conta nasce não verificada e só existe de verdade quando o"
                            + " link for aberto. Responde igual para e-mail novo e já cadastrado —"
                            + " do contrário viraria consulta de quem tem conta no app.")
    public ResponseEntity<Void> cadastrar(
            @Valid @RequestBody CadastroRequest body, HttpServletRequest request) {
        if (!freiar(request, body.email())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        senha.register(body.email(), body.senha(), baseUrl());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/verificar/{token}")
    @Operation(summary = "Confirma o e-mail e abre a sessão")
    public void verificar(
            @PathVariable String token, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String email = senha.verify(token).orElse(null);
        if (email == null) {
            response.sendRedirect(LINK_INVALIDO);
            return;
        }
        sessao.signIn(new PasswordAuthenticationToken(email), request, response);
        response.sendRedirect(APOS_CONFIRMAR);
    }

    @PostMapping("/verificacao/reenviar")
    @Operation(
            summary = "Manda outro link de confirmação",
            description =
                    "O link anterior deixa de valer. Responde igual para cadastro pendente,"
                            + " confirmado e inexistente.")
    public ResponseEntity<Void> reenviar(
            @Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        if (!freiar(request, body.email())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        senha.resendVerification(body.email(), baseUrl());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    @Operation(
            summary = "Entra com e-mail e senha",
            description =
                    "401 sem dizer se o e-mail existe; 403 quando a senha confere mas o endereço"
                            + " ainda não foi confirmado; 429 no freio de tentativas.")
    public ResponseEntity<Void> entrar(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        String email = senha.authenticate(body.email(), body.senha(), request.getRemoteAddr());
        sessao.signIn(new PasswordAuthenticationToken(email), request, response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/senha/esquecida")
    @Operation(
            summary = "Manda o link de redefinição",
            description = "Responde igual para endereço com conta e sem conta.")
    public ResponseEntity<Void> esquecida(
            @Valid @RequestBody EmailRequest body, HttpServletRequest request) {
        if (!freiar(request, body.email())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        senha.requestReset(body.email(), baseUrl());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/senha/redefinir/{token}")
    @Operation(
            summary = "O link de redefinição ainda vale?",
            description =
                    "Consulta que não gasta o link: consumir na abertura da tela o queimaria em"
                            + " qualquer pré-carregamento do navegador ou do cliente de e-mail.")
    public LinkView conferir(@PathVariable String token) {
        return new LinkView(senha.isResetLinkValid(token));
    }

    @PostMapping("/senha/redefinir/{token}")
    @Operation(
            summary = "Troca a senha",
            description =
                    "Queima os links pendentes da conta e encerra as sessões abertas nela. Não abre"
                            + " sessão: a próxima tela é o login, com a senha nova.")
    public ResponseEntity<Void> redefinir(
            @PathVariable String token, @Valid @RequestBody SenhaRequest body) {
        String email = senha.resetPassword(token, body.senha());
        // Depois da troca, e não antes: derrubar sessão de uma redefinição que ainda pode falhar
        // expulsaria a pessoa sem trocar nada.
        sessao.endSessionsOf(senha.subjectsOf(email));
        return ResponseEntity.noContent().build();
    }

    /**
     * Freio por IP <b>e</b> por e-mail, como nas rotas da #52.
     *
     * <p>Os dois recortes cobrem ataques diferentes: o de e-mail impede encher uma caixa alheia, o
     * de IP impede varrer endereços. O segundo vale o que a chave valer enquanto a #77 não corrigir
     * a origem do IP atrás do nginx.
     */
    private boolean freiar(HttpServletRequest request, String email) {
        Instant agora = Instant.now(clock);
        freio.evictOlderThan(JANELA_EMAILS, agora);
        return freio.tryAcquire(
                        "ip:" + request.getRemoteAddr(),
                        MAX_EMAILS_POR_JANELA,
                        JANELA_EMAILS,
                        agora)
                && freio.tryAcquire(
                        "email:" + EmailAccessService.normalize(email),
                        MAX_EMAILS_POR_JANELA,
                        JANELA_EMAILS,
                        agora);
    }

    /** A URL pública que o browser usou — a mesma que monta o redirect do OAuth. */
    private static String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
