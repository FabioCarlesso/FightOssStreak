package dev.fos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Os decodificadores dos tokens que o login nativo do app traz (#139, D68).
 *
 * <p>Conferem a assinatura contra as chaves públicas do provedor e o prazo; quem confere emissor,
 * audiência, nonce e {@code email_verified} é o serviço de cada provedor, que é onde mora a regra.
 *
 * <p>Bean, e não {@code new} dentro do serviço, por causa dos testes: eles trocam estes dois por um
 * decodificador com chave local, e o caminho inteiro — controller, serviço, conta, token — roda sem
 * rede. As chaves só são buscadas na primeira decodificação, então subir a aplicação sem nenhum
 * provedor configurado não toca a rede.
 */
@Configuration
class MobileIdTokenConfig {

    static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    static final String APPLE_JWKS = "https://appleid.apple.com/auth/keys";

    @Bean
    JwtDecoder googleIdTokenDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS).build();
    }

    @Bean
    JwtDecoder appleIdTokenDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(APPLE_JWKS).build();
    }
}
