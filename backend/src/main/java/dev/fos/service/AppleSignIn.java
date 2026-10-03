package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.model.AppleCredential;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppleCredentialRepository;
import dev.fos.repo.UserIdentityRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign in with Apple, só no app iOS (#139, D68).
 *
 * <p>Três responsabilidades, e as três são a decisão:
 *
 * <ol>
 *   <li><b>Conferir o identity token</b>: emissor, audiência igual ao bundle id e o <b>nonce</b>. O
 *       app sorteia o nonce, manda o hash dele para a Apple e o valor cru para cá; sem conferir, um
 *       identity token capturado poderia ser reapresentado por outra pessoa.
 *   <li><b>O e-mail relay não vincula conta.</b> Quem escolhe esconder o endereço recebe um
 *       {@code @privaterelay.appleid.com} que a Apple afirma verificado, mas que não é de mais
 *       ninguém. Tratado como não verificado, ele não se anexa a conta nenhuma e nunca semeia
 *       administração — que é o que a D68 promete, e não depende de ninguém lembrar de não pôr um
 *       endereço relay na {@code FOS_OWNER_EMAILS}.
 *   <li><b>Revogar na Apple quando a conta é excluída</b>, que é a orientação da Apple para app com
 *       Sign in with Apple. O refresh token vem da troca do código do primeiro login.
 * </ol>
 */
@Service
public class AppleSignIn {

    private static final Logger log = LoggerFactory.getLogger(AppleSignIn.class);

    static final String ISSUER = "https://appleid.apple.com";
    static final String RELAY_DOMAIN = "@privaterelay.appleid.com";

    private final JwtDecoder decoder;
    private final FosProperties.Apple apple;
    private final AppleTokenApi api;
    private final AppleCredentialRepository credentials;
    private final UserIdentityRepository identities;
    private final Clock clock;

    public AppleSignIn(
            @Qualifier("appleIdTokenDecoder") JwtDecoder decoder,
            FosProperties properties,
            AppleTokenApi api,
            AppleCredentialRepository credentials,
            UserIdentityRepository identities,
            Clock clock) {
        this.decoder = decoder;
        this.apple = properties.mobile().apple();
        this.api = api;
        this.credentials = credentials;
        this.identities = identities;
        this.clock = clock;
    }

    public boolean isEnabled() {
        return apple.isConfigured();
    }

    /**
     * @param rawNonce o nonce que o app sorteou, em claro — a Apple devolve o hash dele no token
     * @param displayName o nome, que a Apple só entrega ao app no primeiro login
     */
    public NativeLogin verify(String identityToken, String rawNonce, String displayName) {
        if (!isEnabled()) {
            throw MobileLoginException.indisponivel();
        }
        if (rawNonce == null || rawNonce.isBlank()) {
            throw MobileLoginException.tokenInvalido();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(identityToken);
        } catch (JwtException e) {
            throw MobileLoginException.tokenInvalido();
        }
        List<String> audience = jwt.getAudience();
        if (!ISSUER.equals(jwt.getClaimAsString("iss"))
                || audience == null
                || !audience.contains(apple.bundleId())
                || !sha256(rawNonce).equals(jwt.getClaimAsString("nonce"))
                || jwt.getSubject() == null) {
            throw MobileLoginException.tokenInvalido();
        }
        String email = Claims.text(jwt, "email");
        boolean relay =
                Claims.isTrue(jwt, "is_private_email")
                        || (email != null && email.toLowerCase(Locale.ROOT).endsWith(RELAY_DOMAIN));
        boolean verified = email != null && !relay && Claims.isTrue(jwt, "email_verified");
        String name = displayName == null || displayName.isBlank() ? null : displayName.trim();
        return new NativeLogin(jwt.getSubject(), email, verified, name);
    }

    /**
     * Guarda o refresh token da identidade, trocando o código do login.
     *
     * <p>Melhor esforço, e de propósito: a falha da Apple aqui não pode impedir a pessoa de entrar.
     * O custo é que aquela identidade, até o próximo login com código, não terá o que revogar na
     * exclusão — e o log diz isso.
     */
    @Transactional
    public void rememberRefreshToken(UserIdentity identity, String authorizationCode) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            return;
        }
        try {
            api.exchange(authorizationCode)
                    .ifPresent(
                            refresh -> {
                                Instant now = Instant.now(clock);
                                credentials
                                        .findByIdentityId(identity.getId())
                                        .ifPresentOrElse(
                                                c -> c.replaceWith(refresh, now),
                                                () ->
                                                        credentials.save(
                                                                new AppleCredential(
                                                                        identity.getId(),
                                                                        refresh,
                                                                        now)));
                            });
        } catch (RuntimeException e) {
            log.warn(
                    "Troca do código da Apple falhou — identidade {} sem refresh token: {}",
                    identity.getId(),
                    e.getClass().getSimpleName());
        }
    }

    /**
     * Revoga na Apple o acesso de todas as identidades Apple da conta — chamado antes de excluí-la.
     *
     * <p>Também melhor esforço: excluir a conta é direito da pessoa, e a Apple fora do ar não pode
     * mantê-la presa aqui. As linhas saem junto com a conta, no {@code AccountService.delete}.
     */
    public void revokeAllOf(Long userId) {
        List<Long> ids = identities.findByUserId(userId).stream().map(UserIdentity::getId).toList();
        if (ids.isEmpty()) {
            return;
        }
        for (AppleCredential credential : credentials.findByIdentityIdIn(ids)) {
            try {
                api.revoke(credential.getRefreshToken());
            } catch (RuntimeException e) {
                log.warn(
                        "Revogação na Apple falhou — identidade {}: {}",
                        credential.getIdentityId(),
                        e.getClass().getSimpleName());
            }
        }
    }

    static String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 é obrigatório em toda JVM", e);
        }
    }
}
