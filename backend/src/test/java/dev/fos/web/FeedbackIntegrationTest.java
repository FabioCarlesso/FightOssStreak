package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.model.AppUser;
import dev.fos.model.Feedback;
import dev.fos.model.FeedbackCategory;
import dev.fos.model.FeedbackStatus;
import dev.fos.model.UserIdentity;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.FeedbackRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.service.AccountService;
import dev.fos.service.DemoAuthenticationToken;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feedback de usuário, ponta a ponta (docs/produto/feedback.md).
 *
 * <p>O que importa provar aqui: quem manda não decide, só o dono lê e decide a fila, o nó
 * referenciado é opcional e precisa existir quando informado, e a demo (D39) não manda feedback —
 * mesma restrição que já nega poder de dono a ela.
 */
@SpringBootTest(properties = "fos.auth.owner-emails=dono@example.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FeedbackIntegrationTest.FixedClockConfig.class)
@Transactional
class FeedbackIntegrationTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private UserIdentityRepository identities;
    @Autowired private FeedbackRepository feedbacks;

    @Test
    @DisplayName("conta comum manda feedback geral, sem nó — e não vê a fila do dono")
    void commonAccountSendsGeneralFeedback() throws Exception {
        login("google", "aluno", "aluno@example.test", "Aluno");

        mockMvc.perform(
                        post("/api/feedback")
                                .with(as("google", "aluno"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"category\":\"SUGESTAO_FUNCIONALIDADE\","
                                                + "\"message\":\"Seria bom ter modo escuro.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("SUGESTAO_FUNCIONALIDADE"))
                .andExpect(jsonPath("$.nodeCode").doesNotExist())
                .andExpect(jsonPath("$.status").value("ABERTO"));

        mockMvc.perform(get("/api/admin/feedback").with(as("google", "aluno")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));
    }

    @Test
    @DisplayName("feedback amarrado a um nó guarda o código; nó inexistente responde 404")
    void feedbackCanReferenceAnExistingNode() throws Exception {
        login("google", "aluno", "aluno@example.test", "Aluno");

        mockMvc.perform(
                        post("/api/feedback")
                                .with(as("google", "aluno"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"category\":\"TROCA_DE_VIDEO\",\"nodeCode\":\"M0.1\","
                                                + "\"message\":\"O vídeo está fora do ar.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeCode").value("M0.1"));

        mockMvc.perform(
                        post("/api/feedback")
                                .with(as("google", "aluno"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"category\":\"CONTEUDO_ERRADO\",\"nodeCode\":\"M9.9\","
                                                + "\"message\":\"nó que não existe\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("node_not_found"));
    }

    @Test
    @DisplayName("o dono vê a fila, da mais antiga para a mais nova, e muda o status")
    void ownerSeesQueueAndDecides() throws Exception {
        login("google", "dono", "dono@example.test", "Dono");
        login("google", "aluno", "aluno@example.test", "Aluno");

        mockMvc.perform(
                        post("/api/feedback")
                                .with(as("google", "aluno"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"category\":\"BUG\",\"message\":\"app trava ao abrir\"}"))
                .andExpect(status().isOk());

        Long id = feedbacks.findAllByOrderByCreatedAtAsc().get(0).getId();

        mockMvc.perform(get("/api/admin/feedback").with(as("google", "dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(id))
                .andExpect(jsonPath("$.items[0].authorLabel").value("Aluno"))
                .andExpect(jsonPath("$.items[0].status").value("ABERTO"));

        mockMvc.perform(
                        post("/api/admin/feedback/" + id + "/status")
                                .with(as("google", "dono"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"RESOLVIDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVIDO"));

        mockMvc.perform(get("/api/admin/feedback").with(as("google", "dono")))
                .andExpect(jsonPath("$.items[0].status").value("RESOLVIDO"));
    }

    @Test
    @DisplayName("conta de demonstração não manda feedback")
    void demoAccountCannotSendFeedback() throws Exception {
        AppUser demo =
                users.save(
                        AppUser.demo(
                                "Demonstração",
                                Instant.parse("2026-08-16T09:00:00Z"),
                                Instant.parse("2026-08-16T11:00:00Z")));
        identities.save(
                new UserIdentity(
                        demo.getId(),
                        DemoAuthenticationToken.PROVIDER,
                        "demo-sub",
                        null,
                        false,
                        "Demonstração",
                        Instant.parse("2026-08-16T09:00:00Z")));

        mockMvc.perform(
                        post("/api/feedback")
                                .with(authentication(new DemoAuthenticationToken("demo-sub")))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"category\":\"OUTRO\",\"message\":\"teste\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("feedback_nao_permitido"));
    }

    @Test
    @DisplayName("quem mandou feedback consegue excluir a conta, e o que escreveu sai junto")
    void authorCanDeleteAccountAndTheirFeedbackGoes() throws Exception {
        AppUser aluno = login("google", "aluno", "aluno@example.test", "Aluno");
        enviar("aluno", "{\"category\":\"BUG\",\"message\":\"app trava ao abrir\"}");

        mockMvc.perform(delete("/api/me").with(as("google", "aluno")).with(csrf()))
                .andExpect(status().isNoContent());
        // A FK só é conferida quando o DELETE chega ao banco; sem o flush a transação do teste
        // nunca o mandaria, e o caso da FOS-04 passaria aqui e estouraria em produção.
        users.flush();

        assertThat(users.findById(aluno.getId())).isEmpty();
        assertThat(feedbacks.findAll()).isEmpty();
    }

    @Test
    @DisplayName("quem decidiu feedback consegue excluir a conta, e o feedback fica na fila")
    void deciderCanDeleteAccountAndFeedbackStays() throws Exception {
        AppUser dono = login("google", "dono", "dono@example.test", "Dono");
        AppUser aluno = login("google", "aluno", "aluno@example.test", "Aluno");
        enviar("aluno", "{\"category\":\"BUG\",\"message\":\"app trava ao abrir\"}");
        Long id = feedbacks.findAllByOrderByCreatedAtAsc().get(0).getId();
        mockMvc.perform(
                        post("/api/admin/feedback/" + id + "/status")
                                .with(as("google", "dono"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"RESOLVIDO\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/me").with(as("google", "dono")).with(csrf()))
                .andExpect(status().isNoContent());
        users.flush();

        assertThat(users.findById(dono.getId())).isEmpty();
        Feedback restante = feedbacks.findById(id).orElseThrow();
        assertThat(restante.getUserId()).isEqualTo(aluno.getId());
        assertThat(restante.getStatus()).isEqualTo(FeedbackStatus.RESOLVIDO);
        assertThat(restante.getDecidedBy()).isNull();
    }

    @Test
    @DisplayName("a fusão de identidade apaga a conta de origem mesmo com feedback nela")
    void mergeDeletesOriginAccountWithFeedback() {
        Instant agora = Instant.parse("2026-08-16T10:00:00Z");
        AppUser dona = login("google", "ana", "ana@example.test", "Ana");
        AppUser origem = users.save(AppUser.forPassword("ana@example.test", agora));
        UserIdentity senha =
                identities.save(
                        new UserIdentity(
                                origem.getId(),
                                "password",
                                "ana@example.test",
                                "ana@example.test",
                                true,
                                "ana@example.test",
                                agora));
        feedbacks.save(new Feedback(origem.getId(), null, FeedbackCategory.OUTRO, "teste", agora));

        accounts.mergeIdentityInto(senha, dona);
        users.flush();

        assertThat(users.findById(origem.getId())).isEmpty();
        assertThat(feedbacks.findAll()).isEmpty();
        assertThat(identities.findByUserId(dona.getId())).hasSize(2);
    }

    private void enviar(String subject, String body) throws Exception {
        mockMvc.perform(
                        post("/api/feedback")
                                .with(as("google", subject))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk());
    }

    private AppUser login(String provider, String subject, String email, String name) {
        return accounts.registerLogin(provider, subject, email, true, name);
    }

    /** Sessão do par (provedor, subject) — o mesmo que o fluxo real deixa na autenticação. */
    private static RequestPostProcessor as(String provider, String subject) {
        return oauth2Login()
                .clientRegistration(
                        ClientRegistration.withRegistrationId(provider)
                                .clientId("test")
                                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                                .redirectUri("{baseUrl}/api/login/oauth2/code/" + provider)
                                .authorizationUri("https://example.test/authorize")
                                .tokenUri("https://example.test/token")
                                .userInfoUri("https://example.test/userinfo")
                                .userNameAttributeName("sub")
                                .build())
                .attributes(attributes -> attributes.put("sub", subject));
    }
}
