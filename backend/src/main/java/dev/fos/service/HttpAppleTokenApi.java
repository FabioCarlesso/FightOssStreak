package dev.fos.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.fos.config.FosProperties;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * As chamadas à Apple pela rede, com o {@code client_secret} assinado na hora (#139, D68).
 *
 * <p>A Apple não tem segredo fixo: o {@code client_secret} é um JWT ES256 assinado com a chave
 * {@code .p8}, e ele vence. Assinar a cada chamada, com prazo de minutos, evita guardar um segredo
 * derivado que valeria por meses.
 */
@Component
class HttpAppleTokenApi implements AppleTokenApi {

    static final String AUDIENCE = "https://appleid.apple.com";
    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";
    private static final Duration SECRET_TTL = Duration.ofMinutes(5);

    private final FosProperties.Apple apple;
    private final Clock clock;
    private final RestClient http = RestClient.create();

    HttpAppleTokenApi(FosProperties properties, Clock clock) {
        this.apple = properties.mobile().apple();
        this.clock = clock;
    }

    @Override
    public Optional<String> exchange(String authorizationCode) {
        MultiValueMap<String, String> form = form();
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");
        Map<?, ?> body =
                http.post()
                        .uri(TOKEN_URL)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(Map.class);
        Object refresh = body == null ? null : body.get("refresh_token");
        return refresh == null ? Optional.empty() : Optional.of(refresh.toString());
    }

    @Override
    public void revoke(String refreshToken) {
        MultiValueMap<String, String> form = form();
        form.add("token", refreshToken);
        form.add("token_type_hint", "refresh_token");
        http.post()
                .uri(REVOKE_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private MultiValueMap<String, String> form() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", apple.bundleId());
        form.add("client_secret", clientSecret(apple, Instant.now(clock)));
        return form;
    }

    /** O {@code client_secret} da Apple: JWT ES256 com {@code kid}, emitido pelo time. */
    static String clientSecret(FosProperties.Apple apple, Instant now) {
        try {
            JWTClaimsSet claims =
                    new JWTClaimsSet.Builder()
                            .issuer(apple.teamId())
                            .subject(apple.bundleId())
                            .audience(AUDIENCE)
                            .issueTime(Date.from(now))
                            .expirationTime(Date.from(now.plus(SECRET_TTL)))
                            .build();
            SignedJWT jwt =
                    new SignedJWT(
                            new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(apple.keyId()).build(),
                            claims);
            jwt.sign(new ECDSASigner(privateKey(apple.privateKey())));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Não foi possível assinar o client_secret da Apple", e);
        }
    }

    /** A chave {@code .p8} é PKCS#8 em PEM; aceita também o conteúdo sem as linhas de moldura. */
    static ECPrivateKey privateKey(String pem) {
        String base64 =
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("\\n", "")
                        .replaceAll("\\s", "");
        try {
            return (ECPrivateKey)
                    KeyFactory.getInstance("EC")
                            .generatePrivate(
                                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "FOS_MOBILE_APPLE_PRIVATE_KEY não é uma chave EC válida", e);
        }
    }
}
