package dev.fos.service;

import dev.fos.config.FosProperties;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * O ID token do login nativo do Google, conferido (#139, D68).
 *
 * <p>O {@code sub} do Google é o mesmo para qualquer client ID, e o provedor gravado é {@code
 * google}, o mesmo da web. Entrar pelo app e pelo navegador cai na mesma identidade, sem depender
 * de vínculo por e-mail.
 */
@Service
public class GoogleIdTokens {

    private static final Set<String> ISSUERS =
            Set.of("accounts.google.com", "https://accounts.google.com");

    private final JwtDecoder decoder;
    private final List<String> clientIds;

    public GoogleIdTokens(
            @Qualifier("googleIdTokenDecoder") JwtDecoder decoder, FosProperties properties) {
        this.decoder = decoder;
        this.clientIds = properties.mobile().googleClientIds();
    }

    public boolean isEnabled() {
        return !clientIds.isEmpty();
    }

    public NativeLogin verify(String idToken) {
        if (!isEnabled()) {
            throw MobileLoginException.indisponivel();
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw MobileLoginException.tokenInvalido();
        }
        String issuer = jwt.getClaimAsString("iss");
        List<String> audience = jwt.getAudience();
        if (issuer == null
                || !ISSUERS.contains(issuer)
                || audience == null
                || audience.stream().noneMatch(clientIds::contains)
                || jwt.getSubject() == null) {
            throw MobileLoginException.tokenInvalido();
        }
        String email = Claims.text(jwt, "email");
        return new NativeLogin(
                jwt.getSubject(),
                email,
                email != null && Claims.isTrue(jwt, "email_verified"),
                Claims.text(jwt, "name"));
    }
}
