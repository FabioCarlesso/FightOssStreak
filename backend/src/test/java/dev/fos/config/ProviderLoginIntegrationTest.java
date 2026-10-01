package dev.fos.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import dev.fos.model.Role;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.service.AccountService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
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
    @Autowired private ProviderLogin providerLogin;
    @Autowired private AccountService accounts;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("login OIDC (Google) grava a identidade e cria a conta já aprovada")
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

        // Desde a #52 quem entra por provedor não passa pela fila.
        AppUser account = users.findById(identity.getUserId()).orElseThrow();
        assertThat(account.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
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

    @Test
    @DisplayName("login sem email_verified (Facebook) grava a identidade como não verificada")
    void missingEmailVerifiedIsUnverified() {
        providerLogin.register("facebook", facebook("fb-1", "alguem@example.test"));

        UserIdentity identity =
                identities.findByProviderAndProviderSubject("facebook", "fb-1").orElseThrow();
        assertThat(identity.isEmailVerified()).isFalse();
        AppUser account = users.findById(identity.getUserId()).orElseThrow();
        assertThat(account.getPrimaryEmail()).isNull();
    }

    @Test
    @DisplayName("login sem email_verified não se anexa à conta dona do e-mail (FOS-03)")
    void missingEmailVerifiedNeverLinks() {
        oidcUserService.loadUser(request("google-sub-dona", "dela@example.test"));
        Long dona =
                identities
                        .findByProviderAndProviderSubject("google", "google-sub-dona")
                        .orElseThrow()
                        .getUserId();

        providerLogin.register("facebook", facebook("fb-impostor", "dela@example.test"));

        Long impostor =
                identities
                        .findByProviderAndProviderSubject("facebook", "fb-impostor")
                        .orElseThrow()
                        .getUserId();
        assertThat(impostor).isNotEqualTo(dona);
        assertThat(identities.findByUserId(dona)).hasSize(1);
    }

    @Test
    @DisplayName("login sem email_verified com e-mail em owner-emails não vira ADMIN (FOS-03)")
    void missingEmailVerifiedNeverPromotes() {
        providerLogin.register("facebook", facebook("fb-dono", "dono@example.test"));
        // Segundo login também: a semente roda em todo login, não só no primeiro.
        providerLogin.register("facebook", facebook("fb-dono", "dono@example.test"));

        AppUser account =
                users.findById(
                                identities
                                        .findByProviderAndProviderSubject("facebook", "fb-dono")
                                        .orElseThrow()
                                        .getUserId())
                        .orElseThrow();
        assertThat(account.getRole()).isEqualTo(Role.USUARIO);
        assertThat(account.hasVerifiedEmail()).isFalse();
    }

    @Test
    @DisplayName("email_verified=false explícito também não vale como verificado")
    void explicitFalseIsUnverified() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", "fb-2");
        attributes.put("email", "x@example.test");
        attributes.put("email_verified", "false");
        assertThat(ProviderLogin.isEmailVerified(oauth2(attributes))).isFalse();
    }

    @Test
    @DisplayName("Google com email_verified=true continua vinculando e promovendo")
    void googleVerifiedStillLinksAndPromotes() {
        AppUser existente =
                accounts.registerLogin("apple", "apple-1", "dono@example.test", true, "Dono");

        oidcUserService.loadUser(request("google-sub-dono", "dono@example.test"));

        UserIdentity google =
                identities
                        .findByProviderAndProviderSubject("google", "google-sub-dono")
                        .orElseThrow();
        assertThat(google.getUserId()).isEqualTo(existente.getId());
        assertThat(users.findById(existente.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("V18: identidade Facebook gravada como verificada deixa de dar ADMIN pela semente")
    void legacyFacebookIdentityNoLongerSeedsAdmin() {
        // O estado que o código antigo deixava: identidade Facebook verificada e a conta dona do
        // endereço. Gravado direto porque o caminho de login novo já não produz isso.
        AppUser conta = accounts.registerLogin("facebook", "fb-antigo", null, false, "Antigo");
        users.flush();
        jdbc.update(
                "UPDATE user_identity SET email = ?, email_verified = TRUE WHERE provider ="
                        + " 'facebook' AND provider_subject = 'fb-antigo'",
                "dono@example.test");
        jdbc.update(
                "UPDATE app_user SET primary_email = ? WHERE id = ?",
                "dono@example.test",
                conta.getId());
        // E uma conta Google ao lado, que tem de sair intacta.
        AppUser google = accounts.registerLogin("google", "g-ok", "ok@example.test", true, "Ok");
        // O SQL abaixo fala com o banco, não com o contexto de persistência.
        users.flush();

        new ResourceDatabasePopulator(
                        new ClassPathResource(
                                "db/migration/V18__facebook_email_nao_verificado.sql"))
                .execute(dataSource);
        accounts.seedAdmins();
        users.flush();

        List<Map<String, Object>> linhas =
                jdbc.queryForList(
                        "SELECT role, primary_email FROM app_user WHERE id = ?", conta.getId());
        assertThat(linhas.get(0).get("role")).isEqualTo("USUARIO");
        assertThat(linhas.get(0).get("primary_email")).isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT primary_email FROM app_user WHERE id = ?",
                                String.class,
                                google.getId()))
                .isEqualTo("ok@example.test");
    }

    /** O que o fluxo OAuth2 puro entrega para o Facebook: sem {@code email_verified}. */
    private static OAuth2User facebook(String id, String email) {
        return oauth2(Map.of("id", id, "email", email, "name", "Alguém"));
    }

    private static OAuth2User oauth2(Map<String, Object> attributes) {
        return new DefaultOAuth2User(List.of(), attributes, "id");
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
