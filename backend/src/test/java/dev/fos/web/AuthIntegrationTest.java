package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import dev.fos.service.EmailAccessService;
import dev.fos.service.EmailAuthenticationToken;
import java.net.URI;
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

    /**
     * Nos testes quem decide é sempre outra conta que não a decidida — ver o guarda em decide().
     */
    private static final long OWNER_DECIDING = -1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountService accounts;
    @Autowired private EmailAccessService emailAccess;
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
                .andExpect(jsonPath("$.providers.length()").value(0))
                // Sem credencial de envio o cadastro por senha (#81) não existe neste ambiente, e a
                // tela mostra o que existe em vez de um formulário que falha no envio.
                .andExpect(jsonPath("$.passwordEnabled").value(false));
    }

    @Test
    @DisplayName("sem segredo nenhum a aplicação sobe, e só o cadastro por senha fica indisponível")
    void withoutAnySecretOnlyPasswordSignupIsUnavailable() throws Exception {
        // Este contexto é o de dev e do CI: nenhum provedor, nenhuma chave de envio. Que ele suba
        // é metade da asserção — a outra é o cadastro responder indisponível em vez de estourar.
        mockMvc.perform(
                        post("/api/auth/cadastro")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"quem-quer@example.test\",\"senha\":\"uma-senha-bem-longa\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("cadastro_indisponivel"));

        // E nada mais quebra: o resto do app segue respondendo como sempre respondeu.
        mockMvc.perform(get("/api/streak").with(as("google", "quem-ja-esta-dentro")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("conta criada por provedor entra direto, sem passar pela fila")
    void providerAccountEntersApproved() throws Exception {
        AppUser user = login("google", "novato", "novato@example.test", "Novato");

        // A inversão da #52: quem chega por provedor já teve a identidade verificada por um
        // terceiro, e a fila passou a existir para quem NÃO tem provedor.
        assertThat(user.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
        assertThat(user.getId()).isNotEqualTo(SEEDED_USER_ID);

        mockMvc.perform(get("/api/streak").with(as("google", "novato"))).andExpect(status().isOk());
        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .with(as("google", "novato"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk());

        // E não é o dono: entrar direto não dá acesso à fila.
        mockMvc.perform(get("/api/admin/solicitacoes").with(as("google", "novato")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));
    }

    @Test
    @DisplayName(
            "pedido por e-mail nasce pendente e recebe 403 com o motivo, sem tocar em progresso")
    void emailRequestIsPending() throws Exception {
        AppUser user = pedidoPorEmail("sem-provedor@example.test");

        assertThat(user.getAccessStatus()).isEqualTo(AccessStatus.PENDENTE);

        mockMvc.perform(get("/api/streak").with(asEmail("sem-provedor@example.test")))
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
        pedidoPorEmail("sem-provedor@example.test");

        mockMvc.perform(get("/api/me").with(asEmail("sem-provedor@example.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessStatus").value("PENDENTE"))
                .andExpect(jsonPath("$.email").value("sem-provedor@example.test"))
                .andExpect(jsonPath("$.provider").value("email"))
                .andExpect(jsonPath("$.owner").value(false));
    }

    @Test
    @DisplayName("conta recusada segue recusada no login seguinte")
    void deniedAccountStaysDenied() throws Exception {
        AppUser user = pedidoPorEmail("recusado@example.test");
        accounts.decide(user.getId(), false, OWNER_DECIDING);

        // Pedir de novo não reabre a porta — a decisão é da conta, não da tentativa.
        emailAccess.requestAccess("recusado@example.test");

        mockMvc.perform(get("/api/streak").with(asEmail("recusado@example.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_recusado"));
    }

    @Test
    @DisplayName("aprovar libera o acesso da conta que estava na fila")
    void approvingOpensTheApp() throws Exception {
        AppUser user = pedidoPorEmail("sem-provedor@example.test");
        accounts.decide(user.getId(), true, OWNER_DECIDING);

        mockMvc.perform(get("/api/streak").with(asEmail("sem-provedor@example.test")))
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
    @DisplayName("outro dono, com outro e-mail, não sequestra o progresso do primeiro")
    void secondOwnerGetsItsOwnAccount() {
        AppUser first = login("google", "dono", "dono@example.test", "Dono");
        AppUser second = login("facebook", "outro-dono", "outro-dono@example.test", "Outro dono");

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

        // Entra (é provedor), mas não vira dono nem adota o progresso semeado.
        assertThat(user.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
        assertThat(user.getId()).isNotEqualTo(SEEDED_USER_ID);
        assertThat(accounts.isOwner(user)).isFalse();
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
    @DisplayName("o mesmo e-mail VERIFICADO em provedores diferentes é uma conta só (D47)")
    void aVerifiedEmailIsTheSameAccount() {
        AppUser google = login("google", "sujeito", "mesmo@example.test", "Sujeito");
        AppUser facebook = login("facebook", "sujeito", "mesmo@example.test", "Sujeito");

        // A D36 dizia o contrário, e estava certa enquanto não havia senha própria: sem ela, duas
        // contas para a mesma pessoa era um incômodo. Com cadastro aberto (D47) vira o defeito de
        // quem se cadastra e encontra a árvore em branco.
        assertThat(facebook.getId()).isEqualTo(google.getId());
        assertThat(identities.findByUserId(google.getId())).hasSize(2);
    }

    @Test
    @DisplayName("e-mail NÃO verificado nunca vincula contas")
    void anUnverifiedEmailNeverLinksAccounts() {
        AppUser google = login("google", "dona-do-endereco", "dela@example.test", "Dona");
        AppUser impostor =
                accounts.registerLogin("facebook", "impostor", "dela@example.test", false, "Nao");

        // É a metade da D36 que a D47 não toca, e é ela que impede que digitar o endereço de
        // outra pessoa num provedor que não verifica e-mail entregue a conta dela.
        assertThat(impostor.getId()).isNotEqualTo(google.getId());
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

    /**
     * @implNote a recusa é conferida também no <em>corpo</em>, e não só no status. Sem um {@code
     *     accessDeniedHandler} o filtro de CSRF responde no formato padrão do Boot, que não tem
     *     {@code message} e põe "Forbidden" onde o cliente lê o código — e a tela mostrava um "403"
     *     pelado no único caso em que dá para dizer o que fazer.
     */
    @Test
    @DisplayName("escrita sem token de CSRF é recusada, com o motivo no corpo; com token, passa")
    void writesRequireCsrf() throws Exception {
        approved("google", "ana", "ana@example.test", "Ana");

        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .with(as("google", "ana"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("csrf_invalido"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .with(as("google", "ana"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk());
    }

    /**
     * O mesmo recurso, escrito com o {@code a} percent-encodado.
     *
     * <p>Pela RFC 3986 {@code %61} <em>é</em> a letra {@code a}: as duas formas endereçam a mesma
     * rota, e o roteamento do Spring decodifica antes de resolver o controller. Quem decidir acesso
     * comparando o caminho cru da requisição (o que o {@code getRequestURI()} do Tomcat devolve)
     * discorda do roteamento e deixa a rota do dono aberta para qualquer conta aprovada.
     *
     * <p>Este teste existe porque o furo já esteve aqui: a fila vazava e uma conta comum liberava
     * quem quisesse — o poder que a D36 reserva ao autor, virando transitivo.
     */
    @Test
    @DisplayName("caminho percent-encodado não contorna a checagem de dono")
    void encodedPathDoesNotBypassTheOwnerCheck() throws Exception {
        approved("google", "ana", "ana@example.test", "Ana");
        login("google", "dono", "dono@example.test", "Dono");
        AppUser novato = pedidoPorEmail("sem-provedor@example.test");

        // URI, e não String: o builder de String reencoda o `%` e o teste passaria por engano.
        mockMvc.perform(get(URI.create("/api/%61dmin/solicitacoes")).with(as("google", "ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));

        mockMvc.perform(
                        post(URI.create("/api/%61dmin/solicitacoes/" + novato.getId() + "/aprovar"))
                                .with(as("google", "ana"))
                                .with(csrf()))
                .andExpect(status().isForbidden());

        // O que de fato importa: ninguém foi liberado por quem não é dono.
        assertThat(users.findById(novato.getId()).orElseThrow().getAccessStatus())
                .isEqualTo(AccessStatus.PENDENTE);
    }

    @Test
    @DisplayName("a fila de solicitações é só do dono")
    void onlyTheOwnerSeesTheQueue() throws Exception {
        approved("google", "ana", "ana@example.test", "Ana");
        login("google", "dono", "dono@example.test", "Dono");
        AppUser novato = pedidoPorEmail("sem-provedor@example.test");

        mockMvc.perform(get("/api/admin/solicitacoes").with(as("google", "ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));

        mockMvc.perform(get("/api/admin/solicitacoes").with(as("google", "dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests.length()").value(1))
                .andExpect(jsonPath("$.requests[0].id").value(novato.getId()))
                .andExpect(jsonPath("$.requests[0].email").value("sem-provedor@example.test"));

        mockMvc.perform(
                        post("/api/admin/solicitacoes/" + novato.getId() + "/aprovar")
                                .with(as("google", "dono"))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests.length()").value(0));

        mockMvc.perform(get("/api/streak").with(asEmail("sem-provedor@example.test")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("sair invalida a sessão")
    void logoutEndsTheSession() throws Exception {
        approved("google", "ana", "ana@example.test", "Ana");

        mockMvc.perform(post("/api/logout").with(as("google", "ana")).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/streak")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("dono que só passou a bater com a lista entra aprovado no login seguinte")
    void ownerRuleAppliesOnLaterLogins() throws Exception {
        // O primeiro login não casa com a lista — é o que acontece quando `owner-emails` ainda está
        // vazia, que é o default documentado, ou quando o e-mail ainda não estava verificado.
        AppUser antes =
                accounts.registerLogin("google", "dono", "dono@example.test", false, "Dono");
        assertThat(antes.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
        assertThat(accounts.isOwner(antes)).isFalse();
        assertThat(antes.getId()).isNotEqualTo(SEEDED_USER_ID);

        AppUser depois = login("google", "dono", "dono@example.test", "Dono");

        // Sem isto a conta do autor ficaria pendente para sempre, sem ninguém para aprová-la, e o
        // progresso semeado ficaria órfão.
        assertThat(depois.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
        assertThat(depois.getId()).isEqualTo(SEEDED_USER_ID);
        assertThat(users.findById(antes.getId())).isEmpty();

        mockMvc.perform(get("/api/streak").with(as("google", "dono"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/solicitacoes").with(as("google", "dono")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("conta comum aprovada não é migrada nem rebaixada em logins seguintes")
    void approvedAccountIsNotTouchedOnLogin() {
        AppUser ana = approved("google", "ana", "ana@example.test", "Ana");

        AppUser denovo = login("google", "ana", "ana@example.test", "Ana");

        assertThat(denovo.getId()).isEqualTo(ana.getId());
        assertThat(denovo.getAccessStatus()).isEqualTo(AccessStatus.APROVADO);
    }

    @Test
    @DisplayName("o dono não consegue recusar a própria conta")
    void ownerCannotLockItselfOut() throws Exception {
        AppUser dono = login("google", "dono", "dono@example.test", "Dono");

        mockMvc.perform(
                        post("/api/admin/solicitacoes/" + dono.getId() + "/recusar")
                                .with(as("google", "dono"))
                                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        // Continua dentro: sem o guarda, a recuperação seria pelo banco.
        mockMvc.perform(get("/api/admin/solicitacoes").with(as("google", "dono")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("solicitação já decidida não é decidida de novo por uma aba velha")
    void decidingTwiceIsRefused() throws Exception {
        login("google", "dono", "dono@example.test", "Dono");
        AppUser novato = pedidoPorEmail("sem-provedor@example.test");
        accounts.decide(novato.getId(), false, SEEDED_USER_ID);

        mockMvc.perform(
                        post("/api/admin/solicitacoes/" + novato.getId() + "/aprovar")
                                .with(as("google", "dono"))
                                .with(csrf()))
                .andExpect(status().isBadRequest());

        // A recusa continua valendo: reabrir acesso a quem foi recusado é o defeito que o guarda
        // existe para impedir.
        mockMvc.perform(get("/api/streak").with(asEmail("sem-provedor@example.test")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("acesso_recusado"));
    }

    @Test
    @DisplayName("o preflight do dev server passa pelo filtro de segurança")
    void devServerPreflightIsAnswered() throws Exception {
        // Preflight não carrega cookie. Com o CORS fora da cadeia de segurança ele morria em 401
        // antes de o MVC ver a requisição — e o dev server não tinha como falar com a API.
        mockMvc.perform(
                        options("/api/streak")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("origem de fora não ganha permissão de CORS")
    void unknownOriginIsRefused() throws Exception {
        mockMvc.perform(
                        options("/api/streak")
                                .header("Origin", "https://site-de-terceiro.example")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    /** Sessão de quem entrou por link de e-mail — o outro método de autenticação (#52). */
    private static RequestPostProcessor asEmail(String email) {
        return authentication(new EmailAuthenticationToken(email));
    }

    /** Pedido de acesso sem provedor: é o único caminho que ainda cria conta pendente. */
    private AppUser pedidoPorEmail(String email) {
        emailAccess.requestAccess(email);
        return users.findById(
                        identities
                                .findByProviderAndProviderSubject(
                                        EmailAuthenticationToken.PROVIDER, email)
                                .orElseThrow()
                                .getUserId())
                .orElseThrow();
    }

    private AppUser login(String provider, String subject, String email, String name) {
        return accounts.registerLogin(provider, subject, email, true, name);
    }

    private AppUser approved(String provider, String subject, String email, String name) {
        AppUser user = login(provider, subject, email, name);
        return user.isApproved() ? user : accounts.decide(user.getId(), true, OWNER_DECIDING);
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
