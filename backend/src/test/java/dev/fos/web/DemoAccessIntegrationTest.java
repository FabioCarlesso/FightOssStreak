package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fos.model.DisclaimerAcceptance;
import dev.fos.model.DrillLog;
import dev.fos.model.ProgressStatus;
import dev.fos.model.QuizAttempt;
import dev.fos.model.QuizQuestion;
import dev.fos.model.Recall;
import dev.fos.model.SrsReview;
import dev.fos.model.UserNodeKey;
import dev.fos.model.UserProgress;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.DisclaimerAcceptanceRepository;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.LoginTokenRepository;
import dev.fos.repo.NodeRepository;
import dev.fos.repo.QuizAttemptRepository;
import dev.fos.repo.QuizQuestionRepository;
import dev.fos.repo.SrsReviewRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.repo.UserProgressRepository;
import dev.fos.service.AccessRateLimiter;
import dev.fos.service.AccountService;
import dev.fos.service.DemoAccessService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acesso demonstrativo, ponta a ponta (#62).
 *
 * <p>O que estes testes perseguem é o que o desenho promete e é fácil de quebrar sem que apareça: a
 * demonstração ser <b>outra conta</b> (a conta-modelo não muda, e duas demonstrações não se
 * enxergam), gravar <b>de verdade</b>, ter <b>datas rebaseadas</b> para não abrir com tudo vencido,
 * não ter <b>poder de dono</b> mesmo clonando a conta do dono, e <b>sumir por inteiro</b> no prazo.
 *
 * <p>E, antes de tudo isso, a costura: a sessão aberta pelo endpoint precisa ser reconhecida pelo
 * {@code CurrentUserProvider}. É o defeito da #51 — o login autentica, o app responde 401 em
 * silêncio —, e por isso todo teste aqui passa pelo MockMvc com a sessão de verdade, nunca chamando
 * o serviço direto.
 */
@SpringBootTest(
        properties = {
            // A conta-modelo é a do dono de propósito: é o pior caso para o teste de poder.
            "fos.auth.owner-emails=modelo@example.test",
            "fos.demo.template-email=modelo@example.test"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DemoAccessIntegrationTest.RelogioQueAnda.class)
@Transactional
class DemoAccessIntegrationTest {

    /** "Hoje" no teste. Fixo porque streak, SRS e o rebase são todos definidos por datas. */
    private static final Instant HOJE = Instant.parse("2026-08-16T10:00:00Z");

    /** Quando a conta-modelo foi curada: 76 dias atrás. É o que o rebase precisa desfazer. */
    private static final LocalDate CURADA_EM = LocalDate.parse("2026-06-01");

    private static final Instant CURADA_EM_INSTANTE = Instant.parse("2026-06-01T09:00:00Z");

    private static final String EMAIL_MODELO = "modelo@example.test";

    @TestConfiguration
    static class RelogioQueAnda {

        static Instant agora = HOJE;

        /** Relógio que anda, para o teste de expiração não depender de esperar de verdade. */
        @Bean
        Clock clock() {
            return new Clock() {
                @Override
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return agora;
                }
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AccountService accounts;
    @Autowired private AccessRateLimiter freio;
    @Autowired private AppUserRepository users;
    @Autowired private UserIdentityRepository identities;
    @Autowired private UserProgressRepository progress;
    @Autowired private SrsReviewRepository reviews;
    @Autowired private DrillLogRepository drills;
    @Autowired private QuizAttemptRepository quizAttempts;
    @Autowired private QuizQuestionRepository quizQuestions;
    @Autowired private DisclaimerAcceptanceRepository disclaimers;
    @Autowired private LoginTokenRepository loginTokens;
    @Autowired private NodeRepository nodes;

    private Long modelo;

    @BeforeEach
    void curarAContaModelo() {
        RelogioQueAnda.agora = HOJE;
        // O freio é singleton e todos os testes vêm do mesmo IP; sem zerar, o segundo já bateria
        // no limite. Janela zero remove tudo que é passado.
        freio.evictOlderThan(Duration.ZERO, HOJE.plusSeconds(1));

        modelo =
                accounts.registerLogin("google", "modelo-sub", EMAIL_MODELO, true, "Autor").getId();

        Long m01 = nodeId("M0.1");
        Long m02 = nodeId("M0.2");

        UserProgress concluido =
                new UserProgress(
                        new UserNodeKey(modelo, m01), ProgressStatus.COMPLETED, CURADA_EM_INSTANTE);
        concluido.setCompletedAt(CURADA_EM_INSTANTE);
        concluido.setLastQuizScore(100);
        concluido.setPinnedNote("Cotovelo colado ao corpo.");
        progress.save(concluido);

        // Agendado para dois dias DEPOIS da curadoria: depois do rebase precisa cair daqui a dois
        // dias, não vencido há 74.
        SrsReview futuro =
                new SrsReview(new UserNodeKey(modelo, m01), CURADA_EM.plusDays(2), 2, 2.5, 1);
        futuro.setLastReviewedOn(CURADA_EM);
        reviews.save(futuro);

        // E este vencia um dia ANTES: depois do rebase precisa aparecer na agenda de hoje.
        SrsReview vencido =
                new SrsReview(new UserNodeKey(modelo, m02), CURADA_EM.minusDays(1), 1, 2.5, 1);
        vencido.setLastReviewedOn(CURADA_EM.minusDays(2));
        reviews.save(vencido);

        drills.save(
                new DrillLog(
                        modelo,
                        m01,
                        CURADA_EM,
                        Recall.EASY,
                        "saiu limpo",
                        true,
                        CURADA_EM,
                        CURADA_EM_INSTANTE));
        quizAttempts.save(new QuizAttempt(modelo, m01, 100, true, CURADA_EM, CURADA_EM_INSTANTE));
        disclaimers.save(new DisclaimerAcceptance(modelo, "test-1", CURADA_EM_INSTANTE));
    }

    @Test
    @DisplayName("o link abre o app autenticado, sem pedir nada ao visitante")
    void openingADemoAuthenticatesRightAway() throws Exception {
        MockHttpSession sessao = abrirDemo();

        // O que de fato prova a costura: a sessão é reconhecida pelo CurrentUserProvider na
        // requisição seguinte, e não só pelo serviço que a criou.
        mockMvc.perform(get("/api/me").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("demo"))
                .andExpect(jsonPath("$.accessStatus").value("APROVADO"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.demoExpiresAt").exists());
        mockMvc.perform(get("/api/streak").session(sessao)).andExpect(status().isOk());
        mockMvc.perform(get("/api/curriculum/tree").session(sessao)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a demonstração é outra conta: a conta-modelo não muda quando o visitante usa")
    void theTemplateAccountIsNeverTouched() throws Exception {
        MockHttpSession sessao = abrirDemo();

        registrarDrill(sessao, "M0.1", Recall.HARD);

        assertThat(drills.countByUserId(modelo))
                .as("o drill do visitante não pode entrar no histórico da conta-modelo")
                .isEqualTo(1);
        SrsReview doModelo =
                reviews.findById(new UserNodeKey(modelo, nodeId("M0.1"))).orElseThrow();
        assertThat(doModelo.getNextReviewOn()).isEqualTo(CURADA_EM.plusDays(2));
        assertThat(doModelo.getRepetitions()).isEqualTo(1);
    }

    @Test
    @DisplayName("duas demonstrações abertas ao mesmo tempo não se enxergam")
    void twoDemosDoNotSeeEachOther() throws Exception {
        MockHttpSession primeira = abrirDemo();
        MockHttpSession segunda = abrirDemo(deOutroIp("10.0.0.2"));

        registrarDrill(primeira, "M0.2", Recall.EASY);

        Long primeiroId = contaDaSessao(primeira);
        Long segundoId = contaDaSessao(segunda);
        assertThat(primeiroId).isNotEqualTo(segundoId);
        assertThat(drills.countByUserId(primeiroId)).isEqualTo(2);
        assertThat(drills.countByUserId(segundoId))
                .as("o drill de um visitante não pode aparecer na demonstração do outro")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a agenda de hoje vem rebaseada: nem vazia, nem cheia de vencidos antigos")
    void theScheduleIsRebasedToToday() throws Exception {
        MockHttpSession sessao = abrirDemo();

        JsonNode agenda = json(sessao, "/api/reviews/today");
        assertThat(agenda.get("today").asText()).isEqualTo("2026-08-16");
        assertThat(agenda.get("dueCount").asInt()).isEqualTo(1);
        assertThat(agenda.get("due").get(0).get("nodeCode").asText()).isEqualTo("M0.2");
        // Um dia de atraso, como na conta-modelo — o rebase preserva a distância entre os
        // eventos, não as datas.
        assertThat(agenda.get("due").get(0).get("daysOverdue").asLong()).isEqualTo(1);

        SrsReview daDemo =
                reviews.findById(new UserNodeKey(contaDaSessao(sessao), nodeId("M0.1")))
                        .orElseThrow();
        assertThat(daDemo.getNextReviewOn()).isEqualTo(LocalDate.parse("2026-08-18"));
        assertThat(daDemo.getLastReviewedOn()).isEqualTo(LocalDate.parse("2026-08-16"));
    }

    @Test
    @DisplayName("o visitante responde quiz, registra drill e vê a revisão ser reagendada")
    void theVisitorReallyWrites() throws Exception {
        MockHttpSession sessao = abrirDemo();
        Long visitante = contaDaSessao(sessao);

        JsonNode no = json(sessao, "/api/nodes/M0.2");
        mockMvc.perform(
                        post("/api/nodes/M0.2/quiz")
                                .session(sessao)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(respostas(no)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        JsonNode drill = registrarDrill(sessao, "M0.2", Recall.EASY);
        assertThat(LocalDate.parse(drill.get("nextReviewOn").asText()))
                .as("registrar drill reagenda a revisão para frente")
                .isAfter(LocalDate.parse("2026-08-16"));

        assertThat(quizAttempts.findByUserIdOrderByAttemptedOnAscIdAsc(visitante)).hasSize(2);
        assertThat(drills.countByUserId(visitante)).isEqualTo(2);
    }

    @Test
    @DisplayName("a demonstração não administra nada, mesmo clonando a conta de owner-emails")
    void theDemoAccountIsNeverOwner() throws Exception {
        MockHttpSession sessao = abrirDemo();

        mockMvc.perform(get("/api/me").session(sessao))
                .andExpect(jsonPath("$.role").value("USUARIO"));
        mockMvc.perform(get("/api/admin/feedback").session(sessao))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));
    }

    @Test
    @DisplayName("o aceite do disclaimer não é copiado — o visitante passa pelo aviso")
    void theDisclaimerAcceptanceIsNotCopied() throws Exception {
        MockHttpSession sessao = abrirDemo();

        mockMvc.perform(get("/api/disclaimer").session(sessao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedVersion").doesNotExist());
        assertThat(disclaimers.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("passado o prazo, a sessão deixa de autenticar")
    void anExpiredDemoStopsAuthenticating() throws Exception {
        MockHttpSession sessao = abrirDemo();
        mockMvc.perform(get("/api/me").session(sessao)).andExpect(status().isOk());

        RelogioQueAnda.agora = HOJE.plus(DemoAccessService.VALIDADE).plusSeconds(1);

        // 401 e não 403: para quem está do outro lado a conta já não existe, e a tela oferece
        // começar de novo em vez de explicar acesso negado.
        mockMvc.perform(get("/api/me").session(sessao))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("nao_autenticado"));
        mockMvc.perform(get("/api/streak").session(sessao)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a varredura apaga a conta vencida por completo, sem deixar linha órfã")
    void theSweepDeletesEverythingOfAnExpiredDemo() throws Exception {
        MockHttpSession sessao = abrirDemo();
        Long vencida = contaDaSessao(sessao);
        registrarDrill(sessao, "M0.1", Recall.OK);
        assertThat(progress.findByIdUserId(vencida)).isNotEmpty();

        RelogioQueAnda.agora = HOJE.plus(DemoAccessService.VALIDADE).plusSeconds(1);
        abrirDemo(deOutroIp("10.0.0.3"));

        assertThat(users.findById(vencida)).isEmpty();
        assertThat(progress.findByIdUserId(vencida)).isEmpty();
        assertThat(reviews.findByIdUserId(vencida)).isEmpty();
        assertThat(drills.countByUserId(vencida)).isZero();
        assertThat(quizAttempts.findByUserIdOrderByAttemptedOnAscIdAsc(vencida)).isEmpty();
        assertThat(identities.findByUserId(vencida)).isEmpty();
        assertThat(disclaimers.findFirstByUserIdAndVersionOrderByAcceptedAtDesc(vencida, "test-1"))
                .isEmpty();
        assertThat(loginTokens.findAll()).isEmpty();
        // E a conta-modelo continua de pé: a varredura só come demonstração.
        assertThat(users.findById(modelo)).isPresent();
    }

    @Test
    @DisplayName("o freio por IP recusa sem criar conta")
    void theRateLimitRefusesWithoutCreatingAnything() throws Exception {
        for (int i = 0; i < DemoAccessService.MAX_POR_IP; i++) {
            abrirDemo();
        }
        long antes = users.count();

        mockMvc.perform(post("/api/demo/sessao").with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("demo_lotado"));

        assertThat(users.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("X-Forwarded-For inventado a cada requisição não contorna o freio por IP (#77)")
    void aForgedForwardedForDoesNotResetTheRateLimit() throws Exception {
        // Endereço novo a cada requisição é o que o cliente escreve, não de onde ele veio: o
        // freio conta todas na mesma chave.
        for (int i = 0; i < DemoAccessService.MAX_POR_IP; i++) {
            abrirDemo(forjando("203.0.113." + i));
        }
        long antes = users.count();

        mockMvc.perform(post("/api/demo/sessao").with(forjando("203.0.113.99")).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("demo_lotado"));

        assertThat(users.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("o teto de demonstrações vivas recusa sem criar conta")
    void theCapRefusesWithoutCreatingAnything() throws Exception {
        for (int i = 0; i < DemoAccessService.MAX_VIVAS; i++) {
            abrirDemo(deOutroIp("10.1.0." + i));
        }
        long antes = users.count();

        mockMvc.perform(post("/api/demo/sessao").with(deOutroIp("10.2.0.1")).with(csrf()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("demo_lotado"));

        assertThat(users.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("quem já está logado não tem a sessão trocada por uma demonstração")
    void anExistingRealSessionIsNotSwappedForADemo() throws Exception {
        long antes = users.count();

        // O caminho existe de verdade: o rodapé do app leva à apresentação, e o botão está lá.
        // Trocar em silêncio seria pior que recusar — a demonstração é cópia da conta-modelo, e
        // pareceria o app de quem clicou.
        mockMvc.perform(
                        post("/api/demo/sessao")
                                .with(
                                        oauth2Login()
                                                // A registration precisa casar com o provedor da
                                                // identidade: é o par (registrationId, subject)
                                                // que o CurrentUserProvider lê.
                                                .clientRegistration(
                                                        ApiIntegrationTest.TEST_REGISTRATION)
                                                .attributes(
                                                        attributes ->
                                                                attributes.put(
                                                                        "sub", "modelo-sub")))
                                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("sessao_existente"));

        assertThat(users.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("mas quem já está numa demonstração pode começar outra")
    void aDemoSessionCanStartAnother() throws Exception {
        MockHttpSession primeira = abrirDemo();
        // Lido antes: o MockMvc devolve a MESMA instância de sessão, e depois da segunda chamada
        // ela já aponta para a conta nova.
        Long contaAnterior = contaDaSessao(primeira);

        MockHttpSession segunda =
                (MockHttpSession)
                        mockMvc.perform(
                                        post("/api/demo/sessao")
                                                .session(primeira)
                                                .with(deOutroIp("10.0.0.9"))
                                                .with(csrf()))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getRequest()
                                .getSession(false);

        assertThat(contaDaSessao(segunda)).isNotEqualTo(contaAnterior);
    }

    @Test
    @DisplayName("as métricas da demonstração são dela; as do autor não se alteram")
    void metricsStayWithEachAccount() throws Exception {
        MockHttpSession sessao = abrirDemo();
        registrarDrill(sessao, "M0.1", Recall.EASY);

        mockMvc.perform(get("/api/metrics/mvp").session(sessao)).andExpect(status().isOk());
        assertThat(drills.countByUserId(modelo)).isEqualTo(1);
        assertThat(quizAttempts.findByUserIdOrderByAttemptedOnAscIdAsc(modelo)).hasSize(1);
    }

    // --- helpers ---

    private MockHttpSession abrirDemo() throws Exception {
        return abrirDemo(request -> request);
    }

    private MockHttpSession abrirDemo(RequestPostProcessor origem) throws Exception {
        var resultado =
                mockMvc.perform(post("/api/demo/sessao").with(origem).with(csrf()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.destino").value("/hoje"))
                        .andExpect(jsonPath("$.expiraEm").exists())
                        .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    /** O freio é por IP; um teste que precisa de várias demonstrações precisa de vários IPs. */
    private static RequestPostProcessor deOutroIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** O que o cliente escreve. Não é origem nenhuma: é texto que ele escolheu (#77). */
    private static RequestPostProcessor forjando(String ip) {
        return request -> {
            request.addHeader("X-Forwarded-For", ip);
            return request;
        };
    }

    private JsonNode registrarDrill(MockHttpSession sessao, String code, Recall recall)
            throws Exception {
        String corpo =
                mockMvc.perform(
                                post("/api/nodes/" + code + "/drill")
                                        .session(sessao)
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"recall\":\""
                                                        + recall
                                                        + "\",\"note\":\"nota do visitante\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(corpo);
    }

    /** A conta por trás da sessão, achada pelo mesmo par (provider, subject) que o app usa. */
    private Long contaDaSessao(MockHttpSession sessao) {
        var contexto =
                (org.springframework.security.core.context.SecurityContext)
                        sessao.getAttribute("SPRING_SECURITY_CONTEXT");
        String subject = contexto.getAuthentication().getName();
        return identities
                .findByProviderAndProviderSubject("demo", subject)
                .orElseThrow()
                .getUserId();
    }

    private JsonNode json(MockHttpSession sessao, String caminho) throws Exception {
        return objectMapper.readTree(
                mockMvc.perform(get(caminho).session(sessao))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    /**
     * Monta as respostas certas do quiz.
     *
     * <p>O gabarito não vem na API e a ordem das alternativas é embaralhada (D16), então o teste
     * consulta o repositório em vez de assumir posição — mesmo caminho do {@code
     * ApiIntegrationTest}.
     */
    private String respostas(JsonNode no) throws Exception {
        ArrayNode respostas = objectMapper.createArrayNode();
        for (JsonNode pergunta : no.get("quiz")) {
            long id = pergunta.get("id").asLong();
            QuizQuestion guardada = quizQuestions.findById(id).orElseThrow();
            ObjectNode resposta = objectMapper.createObjectNode();
            resposta.put("questionId", id);
            resposta.put("optionId", guardada.correctOption().getId());
            respostas.add(resposta);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("answers", respostas);
        return objectMapper.writeValueAsString(payload);
    }

    private Long nodeId(String code) {
        return nodes.findByCode(code).orElseThrow().getId();
    }
}
