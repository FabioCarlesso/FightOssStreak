package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.DisclaimerAcceptanceRepository;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.QuizAttemptRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.repo.UserProgressRepository;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem entra, quem espera na fila e quem não entra.
 *
 * <p>O {@link ApiIntegrationTest} descreve o app funcionando para quem está dentro; aqui está a
 * fronteira: 401 sem sessão, 403 sem aprovação, isolamento entre contas, exclusão e a fila do dono.
 * São os defeitos caros — deixar passar quem não devia, ou deixar uma conta ver o progresso da
 * outra.
 *
 * <p>Nenhum provedor de login está configurado nesta subida, de propósito: é o cenário de dev e do
 * CI, e o contexto precisa subir sem segredo nenhum. As sessões são simuladas pelo par
 * (registrationId, subject), que é exatamente o que o {@code CurrentUserProvider} lê.
 */
@SpringBootTest(properties = "fos.auth.owner-emails=dono@example.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthIntegrationTest.FixedClockConfig.class)
@Transactional
class AuthIntegrationTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final long SEEDED_USER_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private UserIdentityRepository identities;
    @Autowired private UserProgressRepository progress;
    @Autowired private SrsReviewRepository reviews;
    @Autowired private DrillLogRepository drills;
    @Autowired private QuizAttemptRepository quizAttempts;
    @Autowired private DisclaimerAcceptanceRepository disclaimers;

    @Test
    @DisplayName("sem sessão, a API responde 401 e não cria conta nenhuma")
    void withoutSessionEverythingIs401() throws Exception {
        long before = users.count();

        mockMvc.perform(get("/api/streak")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/curriculum/tree")).andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isUnauthorized());

        // O ponto todo da mudança: 401 é resposta, não convite para criar usuário.
        assertThat(users.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("a lista de provedores é pública e vem vazia sem credencial configurada")
    void providersAreEmptyWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(0));
    }

    @Test
    @DisplayName("conta nova nasce pendente e recebe 403 com o motivo, sem tocar em progresso")
    void newAccountIsPending() throws Exception {
        AppUser user = login("google", "novato", "novato@example.test", "Novato");

        assertThat(user.getAccessStatus()).isEqualTo(AccessStatus.PENDENTE);
        assertThat(user.getId()).isNotEqualTo(SEEDED_USER_ID);

        mockMvc.perform(get("/api/streak").with(as("google", "novato")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_pendente"));
        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .with(as("google", "novato"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_pendente"));

        // Pendente não tem estado no app: nada de progresso, SRS, drill ou aceite.
        assertThat(progress.findByIdUserId(user.getId())).isEmpty();
        assertThat(reviews.findByIdUserId(user.getId())).isEmpty();
        assertThat(drills.countByUserId(user.getId())).isZero();
    }

    @Test
    @DisplayName("quem está na fila ainda enxerga a própria conta, para saber que está esperando")
    void pendingAccountCanStillSeeItself() throws Exception {
        login("google", "novato", "novato@example.test", "Novato");

        mockMvc.perform(get("/api/me").with(as("google", "novato")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessStatus").value("PENDENTE"))
                .andExpect(jsonPath("$.displayName").value("Novato"))
                .andExpect(jsonPath("$.provider").value("google"))
                .andExpect(jsonPath("$.owner").value(false));
    }

    @Test
    @DisplayName("conta recusada segue recusada no login seguinte")
    void deniedAccountStaysDenied() throws Exception {
        AppUser user = login("google", "recusado", "recusado@example.test", "Recusado");
        accounts.decide(user.getId(), false);

        // Entrar de novo pelo provedor não reabre a porta — a decisão é da conta, não da sessão.
        login("google", "recusado", "recusado@example.test", "Recusado");

        mockMvc.perform(get("/api/streak").with(as("google", "recusado")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_recusado"));
    }

    @Test
    @DisplayName("aprovar libera o acesso da conta que estava na fila")
    void approvingOpensTheApp() throws Exception {
        AppUser user = login("google", "novato", "novato@example.test", "Novato");
        accounts.decide(user.getId(), true);

        mockMvc.perform(get("/api/streak").with(as("google", "novato")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStreak").value(0));
    }

    @Test
    @DisplayName("e-mail verificado de dono entra aprovado e adota o progresso pré-existente")
    void ownerAdoptsSeededProgress() throws Exception {
        AppUser owner = login("google", "dono", "dono@example.test", "Dono");

        assertThat(owner.getId()).isEqualTo(SEEDED_USER_ID);
        assertThat(owner.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);

        mockMvc.perform(get("/api/me").with(as("google", "dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value(true));
    }

    @Test
    @DisplayName("o segundo dono não sequestra o progresso do primeiro")
    void secondOwnerGetsItsOwnAccount() {
        AppUser first = login("google", "dono", "dono@example.test", "Dono");
        AppUser second = login("facebook", "outro-dono", "dono@example.test", "Dono no Facebook");

        assertThat(first.getId()).isEqualTo(SEEDED_USER_ID);
        assertThat(second.getId()).isNotEqualTo(SEEDED_USER_ID);
        assertThat(second.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
    }

    @Test
    @DisplayName("e-mail de dono não verificado não vira conta de dono")
    void unverifiedOwnerEmailIsNotOwner() {
        AppUser user =
                accounts.registerLogin(
                        "google", "impostor", "dono@example.test", false, "Impostor");

        assertThat(user.getAccessStatus()).isEqualTo(AccessStatus.PENDENTE);
        assertThat(user.getId()).isNotEqualTo(SEEDED_USER_ID);
    }

    @Test
    @DisplayName("o segundo login da mesma identidade não cria outra conta")
    void loginIsIdempotent() {
        long before = users.count();

        AppUser first = login("google", "novato", "novato@example.test", "Novato");
        AppUser again = login("google", "novato", "novato@example.test", "Novato");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(users.count()).isEqualTo(before + 1);
        assertThat(identities.findByUserId(first.getId())).hasSize(1);
    }

    @Test
    @DisplayName("o mesmo e-mail em provedores diferentes são duas contas")
    void emailIsNotIdentity() {
        AppUser google = login("google", "sujeito", "mesmo@example.test", "Sujeito");
        AppUser facebook = login("facebook", "sujeito", "mesmo@example.test", "Sujeito");

        assertThat(facebook.getId()).isNotEqualTo(google.getId());
    }

    @Test
    @DisplayName("duas contas aprovadas têm progresso, streak e agenda independentes")
    void accountsAreIsolated() throws Exception {
        approved("google", "ana", "ana@example.test", "Ana");
        approved("google", "bruno", "bruno@example.test", "Bruno");

        drill("google", "ana", "M0.1");

        mockMvc.perform(get("/api/streak").with(as("google", "ana")))
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.drilledToday").value(true));
        mockMvc.perform(get("/api/streak").with(as("google", "bruno")))
                .andExpect(jsonPath("$.currentStreak").value(0))
                .andExpect(jsonPath("$.drilledToday").value(false));
        mockMvc.perform(get("/api/reviews/today").with(as("google", "bruno")))
                .andExpect(jsonPath("$.dueCount").value(0));
    }

    @Test
    @DisplayName("excluir a conta apaga tudo que é dela e não toca na outra")
    void deletingAnAccountRemovesOnlyItsOwnData() throws Exception {
        AppUser ana = approved("google", "ana", "ana@example.test", "Ana");
        AppUser bruno = approved("google", "bruno", "bruno@example.test", "Bruno");
        drill("google", "ana", "M0.1");
        drill("google", "bruno", "M0.1");
        accept("google", "ana");

        mockMvc.perform(delete("/api/me").with(as("google", "ana")).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(users.findById(ana.getId())).isEmpty();
        assertThat(identities.findByUserId(ana.getId())).isEmpty();
        assertThat(progress.findByIdUserId(ana.getId())).isEmpty();
        assertThat(reviews.findByIdUserId(ana.getId())).isEmpty();
        assertThat(drills.countByUserId(ana.getId())).isZero();
        assertThat(quizAttempts.findByUserIdOrderByAttemptedOnAscIdAsc(ana.getId())).isEmpty();
        assertThat(
                        disclaimers.findFirstByUserIdAndVersionOrderByAcceptedAtDesc(
                                ana.getId(), "test-1"))
                .isEmpty();

        // A outra conta segue intacta: exclusão é da conta, não do banco.
        assertThat(users.findById(bruno.getId())).isPresent();
        assertThat(drills.countByUserId(bruno.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("conta pendente também consegue se excluir")
    void pendingAccountCanDeleteItself() throws Exception {
        AppUser user = login("google", "novato", "novato@example.test", "Novato");

        mockMvc.perform(delete("/api/me").with(as("google", "novato")).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(users.findById(user.getId())).isEmpty();
    }

    @Test
    @DisplayName("escrita sem token de CSRF é recusada; com token, passa")
    void writesRequireCsrf() throws Exception {
        approved("google", "ana", "ana@example.test", "Ana");

        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .with(as("google", "ana"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .with(as("google", "ana"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a fila de solicitações é só do dono")
    void onlyTheOwnerSeesTheQueue() throws Exception {
        approved("google", "ana", "ana@example.test", "Ana");
        login("google", "dono", "dono@example.test", "Dono");
        AppUser novato = login("google", "novato", "novato@example.test", "Novato");

        mockMvc.perform(get("/api/admin/solicitacoes").with(as("google", "ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));

        mockMvc.perform(get("/api/admin/solicitacoes").with(as("google", "dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests.length()").value(1))
                .andExpect(jsonPath("$.requests[0].id").value(novato.getId()))
                .andExpect(jsonPath("$.requests[0].email").value("novato@example.test"));

        mockMvc.perform(
                        post("/api/admin/solicitacoes/" + novato.getId() + "/aprovar")
                                .with(as("google", "dono"))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests.length()").value(0));

        mockMvc.perform(get("/api/streak").with(as("google", "novato"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("sair invalida a sessão")
    void logoutEndsTheSession() throws Exception {
        approved("google", "ana", "ana@example.test", "Ana");

        mockMvc.perform(post("/api/logout").with(as("google", "ana")).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/streak")).andExpect(status().isUnauthorized());
    }

    private AppUser login(String provider, String subject, String email, String name) {
        return accounts.registerLogin(provider, subject, email, true, name);
    }

    private AppUser approved(String provider, String subject, String email, String name) {
        AppUser user = login(provider, subject, email, name);
        return user.isApproved() ? user : accounts.decide(user.getId(), true);
    }

    /** Sessão do par (provedor, subject) — o mesmo que o fluxo real deixa na autenticação. */
    private static RequestPostProcessor as(String provider, String subject) {
        return oauth2Login()
                .clientRegistration(
                        org.springframework.security.oauth2.client.registration.ClientRegistration
                                .withRegistrationId(provider)
                                .clientId("test")
                                .authorizationGrantType(
                                        org.springframework.security.oauth2.core
                                                .AuthorizationGrantType.AUTHORIZATION_CODE)
                                .redirectUri("{baseUrl}/api/login/oauth2/code/" + provider)
                                .authorizationUri("https://example.test/authorize")
                                .tokenUri("https://example.test/token")
                                .userInfoUri("https://example.test/userinfo")
                                .userNameAttributeName("sub")
                                .build())
                .attributes(attributes -> attributes.put("sub", subject));
    }

    private void drill(String provider, String subject, String node) throws Exception {
        mockMvc.perform(
                        post("/api/nodes/" + node + "/drill")
                                .with(as(provider, subject))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk());
    }

    private void accept(String provider, String subject) throws Exception {
        mockMvc.perform(
                        post("/api/disclaimer/accept")
                                .with(as(provider, subject))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":\"test-1\"}"))
                .andExpect(status().isOk());
    }
}
