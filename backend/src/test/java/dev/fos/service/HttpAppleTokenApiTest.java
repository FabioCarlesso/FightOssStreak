package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import dev.fos.config.FosProperties;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O {@code client_secret} da Apple (#139, D68): JWT ES256 assinado com a chave {@code .p8}.
 *
 * <p>Sem rede: a Apple só confere o que este teste confere — algoritmo, {@code kid}, emissor igual
 * ao time, sujeito igual ao bundle id, audiência e prazo curto — e a assinatura contra a chave.
 */
class HttpAppleTokenApiTest {

    @Test
    @DisplayName("assina com ES256 e o kid, emitido pelo time, para o bundle id, com prazo curto")
    void signsTheClientSecretTheWayAppleExpects() throws Exception {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
        gerador.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair chave = gerador.generateKeyPair();
        String pem =
                "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getMimeEncoder().encodeToString(chave.getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----";
        FosProperties.Apple apple =
                new FosProperties.Apple("dev.fos.app", "TEAM123", "KEY123", pem);
        Instant agora = Instant.parse("2026-10-03T10:00:00Z");

        SignedJWT jwt = SignedJWT.parse(HttpAppleTokenApi.clientSecret(apple, agora));

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEY123");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("TEAM123");
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("dev.fos.app");
        assertThat(jwt.getJWTClaimsSet().getAudience())
                .containsExactly("https://appleid.apple.com");
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant())
                .isBefore(agora.plusSeconds(3600));
        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) chave.getPublic()))).isTrue();
    }

    @Test
    @DisplayName("chave que não é EC falha com a variável no nome, sem expor o valor")
    void anInvalidKeyNamesTheVariable() {
        assertThatThrownBy(() -> HttpAppleTokenApi.privateKey("nao-e-chave"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FOS_MOBILE_APPLE_PRIVATE_KEY")
                .hasMessageNotContaining("nao-e-chave");
    }
}
