package dev.fos.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.UserIdentityRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * A costura entre "entrou no provedor" e "existe conta no app".
 *
 * <p>Este teste existe porque a falta dele custou caro. O {@code AuthIntegrationTest} simula a
 * sessão e chama {@code AccountService.registerLogin} direto, o que é certo para testar o portão —
 * e deixa esta costura sem cobertura nenhuma. Em produção, o Google (que pede {@code openid}) caía
 * no fluxo OIDC, para o qual nenhum serviço de usuário estava registrado: autenticava, voltava para
 * o app e nunca gravava a identidade, então toda chamada respondia 401. Sem erro no log.
 *
 * <p>A {@code ClientRegistration} aqui não declara {@code userInfoUri} de propósito: assim o {@code
 * OidcUserService} monta o usuário só com as claims do id token e o teste não toca a rede.
 */
@SpringBootTest(properties = "fos.auth.owner-emails=dono@example.test")
@ActiveProfiles("test")
@Transactional
class ProviderLoginIntegrationTest {

    @Autowired private FosOidcUserService oidcUserService;
    @Autowired private UserIdentityRepository identities;
    @Autowired private AppUserRepository users;

    @Test
    @DisplayName("login OIDC (Google) grava a identidade e cria a conta")
    void oidcLoginCreatesTheAccount() {
        OidcUser user = oidcUserService.loadUser(request("google-sub-1", "novato@example.test"));

        assertThat(user.getSubject()).isEqualTo("google-sub-1");

        UserIdentity identity =
                identities
                        .findByProviderAndProviderSubject("google", "google-sub-1")
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "o login não gravou a identidade — é exatamente o"
                                                        + " defeito que quebrou produção"));
        assertThat(identity.getEmail()).isEqualTo("novato@example.test");
        assertThat(identity.isEmailVerified()).isTrue();
        assertThat(identity.getDisplayName()).isEqualTo("Novato");

        AppUser account = users.findById(identity.getUserId()).orElseThrow();
        assertThat(account.getAccessStatus()).isEqualTo(AccessStatus.PENDENTE);
    }

    @Test
    @DisplayName("login OIDC do dono entra aprovado")
    void oidcOwnerIsApproved() {
        oidcUserService.loadUser(request("google-sub-dono", "dono@example.test"));

        UserIdentity identity =
                identities
                        .findByProviderAndProviderSubject("google", "google-sub-dono")
                        .orElseThrow();
        AppUser account = users.findById(identity.getUserId()).orElseThrow();
        assertThat(account.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
    }

    @Test
    @DisplayName("o segundo login OIDC da mesma identidade não cria outra conta")
    void oidcLoginIsIdempotent() {
        long antes = users.count();
        oidcUserService.loadUser(request("google-sub-1", "novato@example.test"));
        oidcUserService.loadUser(request("google-sub-1", "novato@example.test"));
        assertThat(users.count()).isEqualTo(antes + 1);
    }

    private static OidcUserRequest request(String subject, String email) {
        Instant agora = Instant.now();
        Instant expira = agora.plusSeconds(300);
        OidcIdToken idToken =
                new OidcIdToken(
                        "id-token",
                        agora,
                        expira,
                        Map.of(
                                StandardClaimNames.SUB,
                                subject,
                                StandardClaimNames.EMAIL,
                                email,
                                StandardClaimNames.EMAIL_VERIFIED,
                                true,
                                StandardClaimNames.NAME,
                                "Novato"));
        OAuth2AccessToken accessToken =
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "access-token",
                        agora,
                        expira,
                        Set.of("openid"));
        return new OidcUserRequest(
                registration(),
                accessToken,
                idToken,
                Map.of(OidcParameterNames.ID_TOKEN, "id-token"));
    }

    /** Sem {@code userInfoUri}: o usuário sai do id token e nada de rede acontece. */
    private static ClientRegistration registration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("test")
                .clientSecret("segredo-de-teste")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/api/login/oauth2/code/google")
                .authorizationUri("https://example.test/authorize")
                .tokenUri("https://example.test/token")
                .jwkSetUri("https://example.test/jwks")
                .userNameAttributeName(StandardClaimNames.SUB)
                .scope("openid", "profile", "email")
                .build();
    }
}
