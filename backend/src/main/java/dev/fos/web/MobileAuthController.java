package dev.fos.web;

import dev.fos.model.UserIdentity;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.service.AccessRateLimiter;
import dev.fos.service.AccountService;
import dev.fos.service.AppleSignIn;
import dev.fos.service.ClientIp;
import dev.fos.service.GoogleIdTokens;
import dev.fos.service.MobileLoginException;
import dev.fos.service.MobileTokens;
import dev.fos.service.NativeLogin;
import dev.fos.service.PasswordAccessService;
import dev.fos.service.PasswordAuthenticationToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login do app mobile (#139, D68): cada rota troca uma prova de identidade por um token.
 *
 * <p>Nenhuma abre sessão nem deixa cookie — o token volta no corpo, e o app o guarda no {@code
 * expo-secure-store}. É por isso que estas rotas ficam fora do CSRF sem abrir nada: outra origem
 * que as chamasse pelo navegador de alguém não leria a resposta, e não sobraria cookie nenhum.
 *
 * <p><b>Cadastro, confirmação e recuperação continuam na web.</b> O link de confirmação sai de
 * {@code fos.public-url} (D62) e exige a senha do cadastro (D61); depois a pessoa entra no app com
 * e-mail e senha.
 */
@RestController
@RequestMapping("/api/mobile/auth")
@Tag(name = "App mobile", description = "Login do app Android e iOS, por token")
public class MobileAuthController {

    /** Por origem, nas rotas de provedor. A de senha tem o freio próprio, por e-mail e por IP. */
    static final int MAX_POR_JANELA = 30;

    static final Duration JANELA = Duration.ofMinutes(15);

    /** Prefixo do freio: a varredura é por prefixo, para não apagar o contador de outra porta. */
    private static final String FREIO = "mobile-login:";

    private final PasswordAccessService senha;
    private final GoogleIdTokens google;
    private final AppleSignIn apple;
    private final AccountService accounts;
    private final UserIdentityRepository identities;
    private final MobileTokens tokens;
    private final AccessRateLimiter freio;
    private final ClientIp clientIp;
    private final Clock clock;

    public MobileAuthController(
            PasswordAccessService senha,
            GoogleIdTokens google,
            AppleSignIn apple,
            AccountService accounts,
            UserIdentityRepository identities,
            MobileTokens tokens,
            AccessRateLimiter freio,
            ClientIp clientIp,
            Clock clock) {
        this.senha = senha;
        this.google = google;
        this.apple = apple;
        this.accounts = accounts;
        this.identities = identities;
        this.tokens = tokens;
        this.freio = freio;
        this.clientIp = clientIp;
        this.clock = clock;
    }

    public record MobilePasswordRequest(@NotBlank String email, @NotBlank String senha) {}

    public record MobileGoogleRequest(@NotBlank String idToken) {}

    /**
     * @param nonce o nonce em claro; a Apple devolve no token o SHA-256 dele
     * @param authorizationCode o código que o backend troca pelo refresh token, para poder revogar
     *     na exclusão da conta. Opcional: sem ele o login funciona, só não há o que revogar depois
     * @param nome o nome, que a Apple só entrega ao app no primeiro login
     */
    public record MobileAppleRequest(
            @NotBlank String identityToken,
            @NotBlank String nonce,
            String authorizationCode,
            String nome) {}

    /** O token do app. Só existe em claro nesta resposta. */
    public record MobileTokenView(String token) {}

    @PostMapping("/senha")
    @Operation(
            summary = "Entra com e-mail e senha e recebe o token do app",
            description =
                    "As mesmas respostas do login da web: 401 sem dizer se o e-mail existe, 403"
                            + " com o endereço ainda não confirmado, 429 no freio de tentativas.")
    public MobileTokenView senha(
            @Valid @RequestBody MobilePasswordRequest body, HttpServletRequest request) {
        String email = senha.authenticate(body.email(), body.senha(), clientIp.of(request));
        UserIdentity identity =
                identities
                        .findByProviderAndProviderSubject(
                                PasswordAuthenticationToken.PROVIDER, email)
                        .orElseThrow();
        return new MobileTokenView(tokens.issue(identity));
    }

    @PostMapping("/google")
    @Operation(
            summary = "Entra pelo login nativo do Google",
            description =
                    "Recebe o ID token do Google e confere assinatura, emissor, prazo e audiência"
                            + " contra os client IDs do app. O e-mail só vincula conta quando o"
                            + " Google afirma email_verified (D63). 404 quando o Google não está"
                            + " configurado neste ambiente; 401 com token que não confere.")
    public MobileTokenView google(
            @Valid @RequestBody MobileGoogleRequest body, HttpServletRequest request) {
        frear(request);
        NativeLogin login = google.verify(body.idToken());
        return new MobileTokenView(tokens.issue(register("google", login)));
    }

    @PostMapping("/apple")
    @Operation(
            summary = "Entra pelo Sign in with Apple (iOS)",
            description =
                    "Confere o identity token, a audiência igual ao bundle id e o nonce. O e-mail"
                            + " relay não vincula conta nem semeia administração. 404 quando a Apple"
                            + " não está configurada neste ambiente; 401 com token que não confere.")
    public MobileTokenView apple(
            @Valid @RequestBody MobileAppleRequest body, HttpServletRequest request) {
        frear(request);
        NativeLogin login = apple.verify(body.identityToken(), body.nonce(), body.nome());
        UserIdentity identity = register("apple", login);
        apple.rememberRefreshToken(identity, body.authorizationCode());
        return new MobileTokenView(tokens.issue(identity));
    }

    @PostMapping("/sair")
    @Operation(
            summary = "Revoga o token do app",
            description =
                    "O token apresentado no cabeçalho Authorization deixa de valer. Os outros"
                            + " aparelhos da conta continuam dentro.")
    public ResponseEntity<Void> sair(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            tokens.revoke(header.substring(7));
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * O mesmo {@code registerLogin} do login por provedor da web: criar conta, anexar pelo e-mail
     * verificado e semear administração saem de um lugar só.
     */
    private UserIdentity register(String provider, NativeLogin login) {
        accounts.registerLogin(
                provider,
                login.subject(),
                login.email(),
                login.emailVerified(),
                login.displayName());
        return identities.findByProviderAndProviderSubject(provider, login.subject()).orElseThrow();
    }

    /** Freio por origem — e a origem sai do {@link ClientIp}, nunca do {@code getRemoteAddr()}. */
    private void frear(HttpServletRequest request) {
        Instant agora = Instant.now(clock);
        freio.evictOlderThan(FREIO, JANELA, agora);
        if (!freio.tryAcquire(FREIO + clientIp.of(request), MAX_POR_JANELA, JANELA, agora)) {
            throw MobileLoginException.muitasTentativas();
        }
    }
}
