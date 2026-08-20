package dev.fos.config;

import dev.fos.config.FosProperties.Provider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Registra os provedores de login que têm credencial configurada — e só esses.
 *
 * <p>As credenciais entram por {@code fos.auth.providers.<provedor>}, e não pelas propriedades
 * padrão do Spring, por dois motivos concretos: (1) declarar uma registration no {@code
 * application.yml} com {@code client-id} vazio faz a aplicação **não subir**, e ela precisa subir
 * em dev e no CI sem nenhum segredo; (2) o redirect fica sob {@code /api} (ver {@link
 * SecurityConfig}), então o template do redirect URI é decisão da aplicação, não de quem preenche a
 * variável de ambiente.
 *
 * <p>Sem nenhum provedor configurado nenhum bean é criado, {@code oauth2Login} não é ligado e a
 * tela de login não mostra botão nenhum.
 */
@Configuration
class OAuth2ProviderConfig {

    /**
     * O redirect vive sob {@code /api} porque é o único caminho que o nginx encaminha ao backend
     * (D23/D24). Os defaults do Spring ({@code /login/oauth2/code/**}) cairiam fora dele.
     */
    static final String REDIRECT_URI = "{baseUrl}/api/login/oauth2/code/{registrationId}";

    @Bean
    @Conditional(AnyProviderConfigured.class)
    ClientRegistrationRepository clientRegistrationRepository(FosProperties properties) {
        List<ClientRegistration> registrations =
                properties.auth().providers().configured().entrySet().stream()
                        .map(entry -> registration(entry.getKey(), entry.getValue()))
                        .toList();
        return new InMemoryClientRegistrationRepository(registrations);
    }

    private static ClientRegistration registration(String id, Provider provider) {
        CommonOAuth2Provider common =
                switch (id.toLowerCase()) {
                    case "google" -> CommonOAuth2Provider.GOOGLE;
                    case "facebook" -> CommonOAuth2Provider.FACEBOOK;
                    // A Apple não é um provedor comum do Spring: o client_secret dela é um JWT
                    // ES256 que a aplicação gera e renova a cada 6 meses, e o redirect chega
                    // por POST cross-site. É trabalho próprio, previsto para a fatia (c) da
                    // #24 — falhar aqui é melhor que registrar uma configuração que não
                    // completa o login.
                    default ->
                            throw new IllegalArgumentException(
                                    "Provedor de login não suportado: "
                                            + id
                                            + ". Suportados: google, facebook.");
                };
        return common.getBuilder(id.toLowerCase())
                .clientId(provider.clientId())
                .clientSecret(provider.clientSecret())
                .redirectUri(REDIRECT_URI)
                .build();
    }

    /**
     * Verdadeiro quando pelo menos um provedor tem id e segredo.
     *
     * <p>Precisa ser {@link Condition} e não {@code @ConditionalOnProperty}: a pergunta é "algum
     * dos provedores", e a lista de provedores é um mapa.
     */
    static class AnyProviderConfigured implements Condition {

        /** Os provedores que a #24 prevê. Um nome fora desta lista nunca chega a ser lido. */
        private static final List<String> KNOWN = List.of("google", "facebook", "apple");

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return KNOWN.stream()
                    .map(
                            id ->
                                    new Provider(
                                            context.getEnvironment()
                                                    .getProperty(
                                                            "fos.auth.providers."
                                                                    + id
                                                                    + ".client-id"),
                                            context.getEnvironment()
                                                    .getProperty(
                                                            "fos.auth.providers."
                                                                    + id
                                                                    + ".client-secret")))
                    .anyMatch(Provider::isConfigured);
        }
    }
}
