package dev.fos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fos.config.FosProperties.Provider;
import dev.fos.config.FosProperties.Providers;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * O que vira registration, e o que falha na cara.
 *
 * <p>Configuração de provedor só é exercida na subida da aplicação, onde um erro vira stack trace
 * de contexto e some no meio do log. Aqui a mesma decisão é testada em milissegundos.
 */
class OAuth2ProviderConfigTest {

    private final OAuth2ProviderConfig config = new OAuth2ProviderConfig();

    @Test
    @DisplayName("provedor configurado vira registration, com o redirect sob /api")
    void configuredProviderBecomesRegistration() {
        InMemoryClientRegistrationRepository repository =
                (InMemoryClientRegistrationRepository)
                        config.clientRegistrationRepository(
                                properties(credencial(), new Provider(null, null)));

        ClientRegistration google = repository.findByRegistrationId("google");
        assertThat(google).isNotNull();
        assertThat(google.getRedirectUri()).isEqualTo(OAuth2ProviderConfig.REDIRECT_URI);
        assertThat(repository.findByRegistrationId("facebook")).isNull();
    }

    @Test
    @DisplayName("credencial pela metade não registra o provedor")
    void halfCredentialsAreIgnored() {
        assertThatThrownBy(
                        () ->
                                config.clientRegistrationRepository(
                                        properties(
                                                new Provider("só-o-id", null),
                                                new Provider(null, null))))
                // Nenhum provedor válido: a lista vazia é recusada pelo próprio repositório do
                // Spring. O caso de "nenhum provedor" de verdade nem chega aqui — a condição do
                // bean é quem impede (ver AnyProviderConfigured).
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("configurar a Apple falha dizendo que ela ainda não é suportada")
    void appleFailsWithAClearMessage() {
        FosProperties properties =
                new FosProperties(
                        "test",
                        new FosProperties.Curriculum(null, false),
                        new FosProperties.Auth(List.of(), new Providers(null, null, credencial())),
                        null);

        // O silêncio seria pior: credencial aceita e botão que nunca aparece. Ver D36 para por que
        // a Apple ficou de fora desta fatia.
        assertThatThrownBy(() -> config.clientRegistrationRepository(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apple")
                .hasMessageContaining("google, facebook");
    }

    private static Provider credencial() {
        return new Provider("id-de-teste", "segredo-de-teste");
    }

    private static FosProperties properties(Provider google, Provider facebook) {
        return new FosProperties(
                "test",
                new FosProperties.Curriculum(null, false),
                new FosProperties.Auth(List.of(), new Providers(google, facebook, null)),
                null);
    }
}
