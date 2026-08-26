package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.model.AppUser;
import dev.fos.model.Role;
import dev.fos.repo.AppUserRepository;
import dev.fos.service.AccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * A administração do app com {@code fos.auth.owner-emails} vazia — o default documentado (D49).
 *
 * <p>Arquivo separado porque a lista é lida na configuração, e não por requisição: o cenário "sem
 * ninguém na variável" é outra subida da aplicação, não outro caso de teste do {@link
 * AdminUsersIntegrationTest}.
 *
 * <p>São duas promessas, e as duas custam caro se quebrarem. A primeira: o app <b>sobe e funciona
 * igual</b> sem a variável — é o ambiente de dev e do CI, onde não há segredo nenhum configurado. A
 * segunda: a semente <b>promove e nunca rebaixa</b>. Desde que o papel virou dado, um deploy com a
 * lista mal preenchida (ou esvaziada de propósito, depois que já há administração pela API) não
 * pode virar perda de acesso à administração do app.
 */
@SpringBootTest(properties = "fos.auth.owner-emails=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminSeedVaziaIntegrationTest.RelogioFixo.class)
@Transactional
class AdminSeedVaziaIntegrationTest {

    private static final Instant AGORA = Instant.parse("2026-08-16T10:00:00Z");

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        Clock clock() {
            return Clock.fixed(AGORA, ZoneOffset.UTC);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;

    @Test
    @DisplayName(
            "com a lista vazia ninguém nasce administrador, e quem já é ADMIN no banco continua sendo")
    void anEmptyOwnerListPromotesNoOneAndDemotesNoOne() throws Exception {
        AppUser conta = accounts.registerLogin("google", "ana", "ana@example.test", true, "Ana");

        // Sem lista, entrar não dá papel nenhum — e o app segue funcionando para todo mundo.
        assertThat(conta.getRole()).isEqualTo(Role.USUARIO);
        mockMvc.perform(get("/api/streak").with(como("ana"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").with(como("ana")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USUARIO"));
        mockMvc.perform(get("/api/admin/usuarios").with(como("ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));

        // Alguém promovido pela API em algum momento — o caminho normal desde a D49.
        conta.changeRole(Role.ADMIN, null, AGORA);
        users.save(conta);

        // E a subida seguinte, com a lista vazia, passa por cima sem rebaixar ninguém: tirar um
        // endereço da variável não é ordem de tirar o papel de quem quer que seja.
        accounts.seedAdmins();

        assertThat(users.findById(conta.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        mockMvc.perform(get("/api/me").with(como("ana")))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(get("/api/admin/usuarios").with(como("ana")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());

        // E continua sendo só quem foi promovido: a lista vazia não abre a administração.
        accounts.registerLogin("google", "bruno", "bruno@example.test", true, "Bruno");
        mockMvc.perform(get("/api/admin/usuarios").with(como("bruno")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));
    }

    /** Sessão do par (provedor, subject) — o mesmo que o fluxo real deixa na autenticação. */
    private static RequestPostProcessor como(String subject) {
        return oauth2Login()
                .clientRegistration(
                        ClientRegistration.withRegistrationId("google")
                                .clientId("test")
                                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                                .redirectUri("{baseUrl}/api/login/oauth2/code/google")
                                .authorizationUri("https://example.test/authorize")
                                .tokenUri("https://example.test/token")
                                .userInfoUri("https://example.test/userinfo")
                                .userNameAttributeName("sub")
                                .build())
                .attributes(attributes -> attributes.put("sub", subject));
    }
}
