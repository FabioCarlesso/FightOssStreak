package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fos.curriculum.CurriculumValidator;
import dev.fos.model.QuizQuestion;
import dev.fos.model.UserIdentity;
import dev.fos.repo.NodeRepository;
import dev.fos.repo.QuizQuestionRepository;
import dev.fos.repo.UserIdentityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Percorre o fluxo real do MVP contra a aplicação inteira: sobe, ingere o currículo versionado, lê
 * a árvore, responde o quiz, registra drill e confere a agenda de revisão.
 *
 * <p>Desde a #24 tudo isso exige sessão. Em vez de espalhar {@code .with(oauth2Login())} por cada
 * chamada, a autenticação e o token de CSRF entram como requisição padrão do MockMvc — o que estes
 * testes descrevem é o app funcionando para quem está dentro, e quem fica de fora é assunto do
 * {@link AuthIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiIntegrationTest.FixedClockConfig.class)
@Transactional
class ApiIntegrationTest {

    /**
     * Relógio fixo: streak e SRS são definidos por datas, então "hoje" precisa ser determinístico.
     */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);
        }

        /** Toda requisição sai autenticada como o usuário aprovado e com token de CSRF. */
        @Bean
        MockMvcBuilderCustomizer authenticatedByDefault() {
            return builder ->
                    builder.defaultRequest(
                            get("/").with(
                                            oauth2Login()
                                                    .clientRegistration(TEST_REGISTRATION)
                                                    .attributes(
                                                            attributes ->
                                                                    attributes.put(
                                                                            "sub", TEST_SUBJECT)))
                                    .with(csrf()));
        }
    }

    /**
     * O provedor não precisa existir de verdade: o que o {@code CurrentUserProvider} lê da
     * autenticação é o par (registrationId, subject), e é ele que a identidade abaixo registra.
     */
    static final ClientRegistration TEST_REGISTRATION =
            ClientRegistration.withRegistrationId("google")
                    .clientId("test")
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/api/login/oauth2/code/google")
                    .authorizationUri("https://example.test/authorize")
                    .tokenUri("https://example.test/token")
                    .userInfoUri("https://example.test/userinfo")
                    .userNameAttributeName("sub")
                    .build();

    static final String TEST_SUBJECT = "autor-de-teste";

    /** A conta semeada pela V2, que a V6 já sobe como APROVADA — o dono do progresso existente. */
    private static final long SEEDED_USER_ID = 1L;

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private QuizQuestionRepository quizQuestionRepository;

    @Autowired private NodeRepository nodeRepository;

    @Autowired private UserIdentityRepository identities;

    /**
     * Dá uma identidade à conta semeada.
     *
     * <p>Sem isto a autenticação padrão não resolve para usuário nenhum e todo teste daqui viraria
     * 401 — o que é o comportamento correto, e está coberto no {@link AuthIntegrationTest}.
     */
    @BeforeEach
    void identifyTestUser() {
        identities.save(
                new UserIdentity(
                        SEEDED_USER_ID,
                        TEST_REGISTRATION.getRegistrationId(),
                        TEST_SUBJECT,
                        "autor@example.test",
                        true,
                        "Autor",
                        Instant.parse("2026-08-16T10:00:00Z")));
    }

    @Test
    @DisplayName("a árvore sobe com os 46 nós do currículo versionado")
    void treeIsServed() throws Exception {
        mockMvc.perform(get("/api/curriculum/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules.length()").value(9))
                .andExpect(jsonPath("$.summary.totalNodes").value(46))
                .andExpect(jsonPath("$.modules[0].code").value("M0"));
    }

    @Test
    @DisplayName("M0 abre desbloqueado e M2.1 começa travado")
    void initialLockState() throws Exception {
        JsonNode tree = getJson("/api/curriculum/tree");

        assertThat(statusOf(tree, "M0.1")).isEqualTo("AVAILABLE");
        assertThat(statusOf(tree, "M1.1")).isEqualTo("LOCKED");
        assertThat(statusOf(tree, "M2.1")).isEqualTo("LOCKED");
    }

    @Test
    @DisplayName("o detalhe do nó traz conceito, aviso de segurança e quiz sem gabarito")
    void nodeDetailHidesAnswerKey() throws Exception {
        JsonNode node = getJson("/api/nodes/M0.3");

        assertThat(node.get("concept").asText()).isNotBlank();
        assertThat(node.get("safetyNotice").asText()).contains("Respeite o tap");
        assertThat(node.get("quiz")).isNotEmpty();

        // O cliente nunca recebe qual alternativa é a correta antes de responder.
        JsonNode option = node.get("quiz").get(0).get("options").get(0);
        assertThat(option.has("correct")).isFalse();
        assertThat(option.has("label")).isTrue();
    }

    @Test
    @DisplayName(
            "o embaralhamento de D16 realmente embaralha: o gabarito não fica sempre na frente")
    void answerKeyIsNotAlwaysServedFirst() throws Exception {
        Map<Integer, Integer> questionsByCorrectPosition = new TreeMap<>();
        int total = 0;

        for (String code : nodeCodesWithQuiz()) {
            for (JsonNode question : getJson("/api/nodes/" + code).get("quiz")) {
                long correctId =
                        quizQuestionRepository
                                .findById(question.get("id").asLong())
                                .orElseThrow()
                                .correctOption()
                                .getId();

                ArrayNode options = (ArrayNode) question.get("options");
                for (int position = 0; position < options.size(); position++) {
                    if (options.get(position).get("id").asLong() == correctId) {
                        questionsByCorrectPosition.merge(position, 1, Integer::sum);
                    }
                }
                total++;
            }
        }

        assertThat(total)
                .as("o currículo precisa ter quiz para este teste dizer algo")
                .isGreaterThan(50);
        assertThat(questionsByCorrectPosition.keySet())
                .as("posições ocupadas pela alternativa correta ao longo do currículo")
                .containsExactly(0, 1, 2, 3);
        assertThat(questionsByCorrectPosition.get(0))
                .as(
                        "gabarito servido em primeiro lugar — era 100%% quando o hash não espalhava bits")
                .isLessThan(total / 2);
    }

    @Test
    @DisplayName("nó ainda sem vídeo catalogado é servido sem quebrar")
    void uncataloguedVideoIsNormalState() throws Exception {
        String code = anyNodeWithVideo(false);
        assumeTrue(code != null, "todo nó já tem vídeo — a curadoria terminou");

        mockMvc.perform(get("/api/nodes/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video.catalogued").value(false))
                .andExpect(jsonPath("$.video.youtubeId").doesNotExist());
    }

    @Test
    @DisplayName("nó com vídeo catalogado traz embed sem cookie, link do watch e crédito ao canal")
    void cataloguedVideoIsServedWithCredit() throws Exception {
        String code = anyNodeWithVideo(true);
        assertThat(code).as("o currículo precisa ter ao menos um vídeo catalogado").isNotNull();

        JsonNode video = getJson("/api/nodes/" + code).get("video");

        assertThat(video.get("catalogued").asBoolean()).isTrue();
        assertThat(video.get("youtubeId").asText()).matches(CurriculumValidator.YOUTUBE_ID_REGEX);
        assertThat(video.get("channel").asText())
                .as("crédito ao canal é requisito da política D7, não enfeite")
                .isNotBlank();
        assertThat(video.get("embedUrl").asText())
                .startsWith("https://www.youtube-nocookie.com/embed/")
                .endsWith(video.get("youtubeId").asText());
        assertThat(video.get("watchUrl").asText())
                .isEqualTo("https://www.youtube.com/watch?v=" + video.get("youtubeId").asText());
    }

    @Test
    @DisplayName("complementares vêm na ordem do JSON, com crédito, miniatura e embed em loop")
    void extraVideosAreServedInCuratedOrder() throws Exception {
        JsonNode extras = getJson("/api/nodes/M1.3").get("extraVideos");

        assertThat(extras).hasSize(3);
        assertThat(extras.get(0).get("youtubeId").asText())
                .as("a ordem é curatorial e vem do JSON versionado, não do banco")
                .isEqualTo("AkQxiCrsGo4");

        JsonNode primeiro = extras.get(0);
        assertThat(primeiro.get("channel").asText())
                .as("crédito ao canal vale para complementar igual (D7)")
                .isEqualTo("Guiabasicodejiujitsu");
        assertThat(primeiro.get("orientation").asText()).isEqualTo("VERTICAL");
        assertThat(primeiro.get("thumbnailUrl").asText())
                .as("a tira mostra miniatura para não montar um player por clipe")
                .isEqualTo("https://i.ytimg.com/vi/AkQxiCrsGo4/hqdefault.jpg");
        assertThat(primeiro.get("embedUrl").asText())
                .startsWith("https://www.youtube-nocookie.com/embed/AkQxiCrsGo4")
                .contains("autoplay=1")
                .as("playlist com o próprio id é o que faz o loop funcionar em vídeo único")
                .contains("loop=1&playlist=AkQxiCrsGo4");
    }

    @Test
    @DisplayName("nó sem complementar traz a lista vazia, não nulo")
    void nodesWithoutExtraVideosGetAnEmptyList() throws Exception {
        JsonNode extras = getJson("/api/nodes/M0.1").get("extraVideos");

        assertThat(extras.isArray()).isTrue();
        assertThat(extras).isEmpty();
    }

    @Test
    @DisplayName("acertar o quiz conclui o nó e desbloqueia o sucessor")
    void passingQuizUnlocksSuccessor() throws Exception {
        completeNode("M0.3");
        completeNode("M0.4");

        JsonNode tree = getJson("/api/curriculum/tree");
        assertThat(statusOf(tree, "M0.3")).isEqualTo("COMPLETED");
        assertThat(statusOf(tree, "M1.1")).as("M1.1 depende de M0.3 e M0.4").isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("errar o quiz devolve explicações e não conclui o nó")
    void failingQuizExplainsWithoutCompleting() throws Exception {
        JsonNode node = getJson("/api/nodes/M0.1");
        String payload = answerPayload(node, false);

        JsonNode result =
                objectMapper.readTree(
                        mockMvc.perform(
                                        post("/api/nodes/M0.1/quiz")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(payload))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.passed").value(false))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());

        assertThat(result.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(result.get("feedback")).isNotEmpty();
        assertThat(result.get("feedback").get(0).get("explanation").asText()).isNotBlank();
    }

    @Test
    @DisplayName("concluir um nó agenda o primeiro drill de revisão")
    void completionSchedulesReview() throws Exception {
        completeNode("M0.1");

        mockMvc.perform(get("/api/nodes/M0.1"))
                .andExpect(jsonPath("$.srs.scheduled").value(true))
                .andExpect(jsonPath("$.srs.nextReviewOn").value("2026-08-17"));
    }

    @Test
    @DisplayName("registrar drill sobe o streak e reagenda o nó")
    void drillFeedsStreakAndSrs() throws Exception {
        completeNode("M0.1");

        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streak.currentStreak").value(1))
                .andExpect(jsonPath("$.streak.drilledToday").value(true))
                .andExpect(jsonPath("$.intervalDays").value(2))
                .andExpect(jsonPath("$.nextReviewOn").value("2026-08-18"));
    }

    @Test
    @DisplayName("um nó vencido aparece na agenda de hoje")
    void overdueNodeAppearsInAgenda() throws Exception {
        completeNode("M0.1");

        // Drill registrado há dias: o reagendamento cai no passado e o nó vence.
        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"FORGOT\",\"drilledOn\":\"2026-08-10\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reviews/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.due[0].nodeCode").value("M0.1"))
                .andExpect(jsonPath("$.due[0].daysOverdue").value(5));
    }

    @Test
    @DisplayName("drill em data futura é recusado")
    void futureDrillIsRejected() throws Exception {
        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\",\"drilledOn\":\"2027-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    @DisplayName("nó sem quiz escrito responde 409, não erro de servidor")
    void nodeWithoutQuizReturnsConflict() throws Exception {
        // Desde a #59 (D44) todos os 46 nós têm banco de quiz; o estado vazio (D15) só existe mais
        // para um nó futuro. Simula esse estado esvaziando o banco de um nó real.
        wipeQuiz("M8.3");

        mockMvc.perform(
                        post("/api/nodes/M8.3/quiz")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"answers\":[{\"questionId\":1,\"optionId\":1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("quiz_unavailable"));
    }

    @Test
    @DisplayName("nó sem quiz é concluído pelo registro de drill")
    void nodeWithoutQuizIsCompletedByDrill() throws Exception {
        wipeQuiz("M8.3");

        mockMvc.perform(
                        post("/api/nodes/M8.3/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName(
            "banco maior que a prova gira: a segunda tentativa serve outras perguntas, e a nona "
                    + "repete a primeira (issue #59, D44)")
    void quizBankRotatesAcrossAttempts() throws Exception {
        Set<Long> firstServed = idsOf(getJson("/api/nodes/M0.1").get("quiz"));

        completeNode("M0.1");
        Set<Long> secondServed = idsOf(getJson("/api/nodes/M0.1").get("quiz"));
        assertThat(secondServed)
                .as("segunda tentativa não pode repetir nenhuma pergunta da primeira")
                .doesNotContainAnyElementsOf(firstServed);

        // Mais 7 tentativas — 8 no total — para completar o ciclo até a nona.
        for (int i = 0; i < 7; i++) {
            completeNode("M0.1");
        }

        Set<Long> ninthServed = idsOf(getJson("/api/nodes/M0.1").get("quiz"));
        assertThat(ninthServed)
                .as("a nona tentativa dá a volta e repete a primeira")
                .isEqualTo(firstServed);
    }

    @Test
    @DisplayName(
            "submeter um quiz já renovado responde 409 e não grava tentativa nem altera o "
                    + "progresso (issue #59, D44)")
    void staleQuizSubmissionIsRejected() throws Exception {
        JsonNode node = getJson("/api/nodes/M0.2");
        String payload = answerPayload(node, true);

        mockMvc.perform(
                        post("/api/nodes/M0.2/quiz")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));

        mockMvc.perform(get("/api/metrics/mvp"))
                .andExpect(jsonPath("$.quizRetakes.value").value(0));
        JsonNode treeBefore = getJson("/api/curriculum/tree");

        // Reenvia o mesmo payload: a rotação já avançou para a próxima fatia, então este conjunto
        // não é mais o servido.
        mockMvc.perform(
                        post("/api/nodes/M0.2/quiz")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("quiz_stale"));

        assertThat(getJson("/api/curriculum/tree"))
                .as("submissão recusada não pode mexer no progresso")
                .isEqualTo(treeBefore);
        // Submissão recusada não pode gravar tentativa: o contador de refeitas continua em zero.
        mockMvc.perform(get("/api/metrics/mvp"))
                .andExpect(jsonPath("$.quizRetakes.value").value(0));
    }

    @Test
    @DisplayName("a anotação fixada é gravada, sobrescrita e devolvida no detalhe do nó")
    void pinnedNoteIsStoredAndServed() throws Exception {
        mockMvc.perform(get("/api/nodes/M0.1"))
                .andExpect(jsonPath("$.pinnedNote").value(nullValue()));

        mockMvc.perform(
                        put("/api/nodes/M0.1/note")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Cotovelo colado antes de virar.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeCode").value("M0.1"))
                .andExpect(jsonPath("$.note").value("Cotovelo colado antes de virar."));

        mockMvc.perform(get("/api/nodes/M0.1"))
                .andExpect(jsonPath("$.pinnedNote").value("Cotovelo colado antes de virar."));

        mockMvc.perform(
                        put("/api/nodes/M0.1/note")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"Olhar para o teto, não para o chão.\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/nodes/M0.1"))
                .andExpect(jsonPath("$.pinnedNote").value("Olhar para o teto, não para o chão."));
    }

    @Test
    @DisplayName("nota só com espaços limpa a anotação em vez de gravar string vazia")
    void blankPinnedNoteClearsIt() throws Exception {
        mockMvc.perform(
                        put("/api/nodes/M0.1/note")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"algo para lembrar\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(
                        put("/api/nodes/M0.1/note")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value(nullValue()));

        mockMvc.perform(get("/api/nodes/M0.1"))
                .andExpect(jsonPath("$.pinnedNote").value(nullValue()));
    }

    @Test
    @DisplayName("anotar não é treinar: nada de status, streak, SRS ou agenda se move")
    void pinnedNoteDoesNotTouchProgress() throws Exception {
        JsonNode summaryBefore = getJson("/api/curriculum/tree").get("summary");

        // M1.1 está travado e M0.1 disponível: os dois estados que a anotação poderia estragar.
        mockMvc.perform(
                        put("/api/nodes/M0.1/note")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"anotação de um nó nunca treinado\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(
                        put("/api/nodes/M1.1/note")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"anotação de um nó travado\"}"))
                .andExpect(status().isOk());

        JsonNode tree = getJson("/api/curriculum/tree");
        assertThat(statusOf(tree, "M0.1")).isEqualTo("AVAILABLE");
        assertThat(statusOf(tree, "M1.1")).isEqualTo("LOCKED");

        // A linha nova em `user_progress` não pode mudar um único número da árvore: é o que
        // garante que gravar AVAILABLE seja equivalente a não ter linha nenhuma.
        assertThat(tree.get("summary")).isEqualTo(summaryBefore);

        mockMvc.perform(get("/api/streak"))
                .andExpect(jsonPath("$.currentStreak").value(0))
                .andExpect(jsonPath("$.drilledToday").value(false))
                .andExpect(jsonPath("$.activeDaysLast30").value(0));

        mockMvc.perform(get("/api/reviews/today")).andExpect(jsonPath("$.dueCount").value(0));

        mockMvc.perform(get("/api/nodes/M0.1"))
                .andExpect(jsonPath("$.srs.scheduled").value(false))
                .andExpect(jsonPath("$.recentDrills.length()").value(0));

        mockMvc.perform(get("/api/metrics/mvp"))
                .andExpect(jsonPath("$.daysWithDrill.value").value(0))
                .andExpect(jsonPath("$.nodesCompleted.value").value(0));
    }

    @Test
    @DisplayName("anotação acima do limite é recusada, não truncada")
    void oversizedPinnedNoteIsRejected() throws Exception {
        String tooLong = "a".repeat(1001);

        mockMvc.perform(
                        put("/api/nodes/M0.1/note")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("note", tooLong))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));

        mockMvc.perform(get("/api/nodes/M0.1"))
                .andExpect(jsonPath("$.pinnedNote").value(nullValue()));
    }

    @Test
    @DisplayName("anotar nó inexistente responde 404")
    void pinnedNoteOnUnknownNodeReturns404() throws Exception {
        mockMvc.perform(
                        put("/api/nodes/M9.9/note")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"note\":\"nó que não existe\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("node_not_found"));
    }

    @Test
    @DisplayName("a nota do drill entra no histórico do nó, junto com data e recall")
    void drillNotesAppearInNodeHistory() throws Exception {
        mockMvc.perform(
                        post("/api/nodes/M8.3/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"recall\":\"HARD\",\"drilledOn\":\"2026-08-10\","
                                                + "\"note\":\"travei na entrada\"}"))
                .andExpect(status().isOk());

        // Sem nota: o histórico registra o treino do mesmo jeito, com `note` nulo.
        mockMvc.perform(
                        post("/api/nodes/M8.3/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/nodes/M8.3"))
                .andExpect(jsonPath("$.recentDrills.length()").value(2))
                .andExpect(jsonPath("$.recentDrills[0].drilledOn").value("2026-08-16"))
                .andExpect(jsonPath("$.recentDrills[0].recall").value("OK"))
                .andExpect(jsonPath("$.recentDrills[0].note").value(nullValue()))
                .andExpect(jsonPath("$.recentDrills[1].drilledOn").value("2026-08-10"))
                .andExpect(jsonPath("$.recentDrills[1].recall").value("HARD"))
                .andExpect(jsonPath("$.recentDrills[1].note").value("travei na entrada"));
    }

    @Test
    @DisplayName("nó inexistente responde 404")
    void unknownNodeReturns404() throws Exception {
        mockMvc.perform(get("/api/nodes/M9.9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("node_not_found"));
    }

    @Test
    @DisplayName("o disclaimer começa não aceito e o aceite é registrado por versão")
    void disclaimerAcceptanceIsVersioned() throws Exception {
        mockMvc.perform(get("/api/disclaimer"))
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.currentVersion").value("test-1"));

        mockMvc.perform(
                        post("/api/disclaimer/accept")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":\"test-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    @DisplayName("aceitar uma versão antiga do aviso é recusado")
    void staleDisclaimerVersionIsRejected() throws Exception {
        mockMvc.perform(
                        post("/api/disclaimer/accept")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":\"2020-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sem uso, as métricas do MVP respondem zerado e com as metas de docs/05")
    void metricsStartEmpty() throws Exception {
        mockMvc.perform(get("/api/metrics/mvp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowDays").value(30))
                .andExpect(jsonPath("$.windowStart").value("2026-07-18"))
                .andExpect(jsonPath("$.windowEnd").value("2026-08-16"))
                .andExpect(jsonPath("$.daysWithDrill.value").value(0))
                .andExpect(jsonPath("$.daysWithDrill.target").value(12))
                .andExpect(jsonPath("$.nodesCompleted.target").value(15))
                .andExpect(jsonPath("$.srsAdherence.targetPercent").value(60))
                // Nada agendado ainda: aderência ausente, não 0% — não houve cobrança a ignorar.
                .andExpect(jsonPath("$.srsAdherence.percent").value(nullValue()));
    }

    @Test
    @DisplayName("concluir um nó e registrar drill move dias com drill e nós concluídos")
    void metricsFollowRealUse() throws Exception {
        completeNode("M0.1");

        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/metrics/mvp"))
                .andExpect(jsonPath("$.daysWithDrill.value").value(1))
                .andExpect(jsonPath("$.nodesCompleted.value").value(1));
    }

    @Test
    @DisplayName("só o drill que atende revisão vencida entra na aderência ao SRS")
    void metricsCountAttendedReviews() throws Exception {
        completeNode("M0.1");

        // Drill retroativo com recall ruim: o nó é reagendado para o passado e passa a estar
        // vencido.
        // Neste registro ele ainda não estava vencido — a revisão era para 17/08.
        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"FORGOT\",\"drilledOn\":\"2026-08-10\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/metrics/mvp"))
                .andExpect(jsonPath("$.srsAdherence.attended").value(0))
                .andExpect(jsonPath("$.srsAdherence.scheduled").value(1))
                .andExpect(jsonPath("$.srsAdherence.percent").value(0));

        // Agora sim: o nó está vencido e o drill de hoje atende à sugestão da agenda.
        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/metrics/mvp"))
                .andExpect(jsonPath("$.srsAdherence.attended").value(1))
                .andExpect(jsonPath("$.srsAdherence.scheduled").value(1))
                .andExpect(jsonPath("$.srsAdherence.percent").value(100))
                .andExpect(jsonPath("$.srsAdherence.met").value(true));
    }

    @Test
    @DisplayName("refazer o quiz de um nó já concluído conta como quiz refeito")
    void metricsCountQuizRetakes() throws Exception {
        // Errar e passar na segunda é o caminho normal de conclusão, não repetição espontânea.
        JsonNode node = getJson("/api/nodes/M0.1");
        mockMvc.perform(
                        post("/api/nodes/M0.1/quiz")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerPayload(node, false)))
                .andExpect(status().isOk());
        completeNode("M0.1");

        mockMvc.perform(get("/api/metrics/mvp"))
                .andExpect(jsonPath("$.quizRetakes.value").value(0));

        completeNode("M0.1");

        mockMvc.perform(get("/api/metrics/mvp"))
                .andExpect(jsonPath("$.quizRetakes.value").value(1))
                .andExpect(jsonPath("$.quizRetakes.met").value(true));
    }

    @Test
    @DisplayName("janela de medição fora do intervalo aceito responde 400")
    void metricsRejectInvalidWindow() throws Exception {
        mockMvc.perform(get("/api/metrics/mvp").param("days", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    // --- helpers ---

    private void completeNode(String code) throws Exception {
        JsonNode node = getJson("/api/nodes/" + code);
        mockMvc.perform(
                        post("/api/nodes/" + code + "/quiz")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(answerPayload(node, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));
    }

    /**
     * Primeiro nó da árvore com (ou sem) vídeo catalogado, ou {@code null} se não houver.
     *
     * <p>Fixar um código aqui amarraria o teste ao estado da curadoria, que muda por design: o
     * escopo catalogado cresce a cada issue e um nó pode voltar a ficar sem vídeo quando a escolha
     * não sobrevive à revisão.
     */
    private String anyNodeWithVideo(boolean catalogued) throws Exception {
        for (JsonNode module : getJson("/api/curriculum/tree").get("modules")) {
            for (JsonNode node : module.get("nodes")) {
                if (node.get("hasVideo").asBoolean() == catalogued) {
                    return node.get("code").asText();
                }
            }
        }
        return null;
    }

    /**
     * Códigos dos nós que já têm quiz escrito, lidos do banco em vez de fixados.
     *
     * <p>O escopo curado cresce a cada issue (hoje M0–M3), e o teste que percorre o quiz inteiro
     * deve acompanhar esse crescimento sozinho.
     */
    private List<String> nodeCodesWithQuiz() {
        return quizQuestionRepository.findAll().stream()
                .map(question -> question.getNode().getCode())
                .distinct()
                .toList();
    }

    /** Esvazia o banco de quiz de um nó real, simulando o estado {@code quiz: []} (D15). */
    private void wipeQuiz(String nodeCode) {
        Long nodeId = nodeRepository.findByCode(nodeCode).orElseThrow().getId();
        quizQuestionRepository.deleteAll(quizQuestionRepository.findByNodeIdWithOptions(nodeId));
    }

    /** Ids das perguntas servidas num payload de nó. */
    private Set<Long> idsOf(JsonNode quiz) {
        return StreamSupport.stream(quiz.spliterator(), false)
                .map(question -> question.get("id").asLong())
                .collect(Collectors.toSet());
    }

    /** Procura o status de um nó dentro do payload da árvore. */
    private String statusOf(JsonNode tree, String nodeCode) {
        for (JsonNode module : tree.get("modules")) {
            for (JsonNode node : module.get("nodes")) {
                if (nodeCode.equals(node.get("code").asText())) {
                    return node.get("status").asText();
                }
            }
        }
        throw new AssertionError("Nó ausente da árvore: " + nodeCode);
    }

    private JsonNode getJson(String path) throws Exception {
        return objectMapper.readTree(
                mockMvc.perform(get(path))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    /**
     * Monta respostas para o quiz.
     *
     * <p>O gabarito não vem na API — de propósito, e a ordem das alternativas é embaralhada. Então
     * o teste consulta o repositório para saber qual é a correta, em vez de assumir posição.
     */
    private String answerPayload(JsonNode node, boolean correct) throws Exception {
        ArrayNode answers = objectMapper.createArrayNode();
        for (JsonNode question : node.get("quiz")) {
            long questionId = question.get("id").asLong();
            QuizQuestion stored = quizQuestionRepository.findById(questionId).orElseThrow();
            long correctId = stored.correctOption().getId();

            long chosen =
                    correct
                            ? correctId
                            : stored.getOptions().stream()
                                    .filter(option -> !option.isCorrect())
                                    .findFirst()
                                    .orElseThrow()
                                    .getId();

            ObjectNode answer = objectMapper.createObjectNode();
            answer.put("questionId", questionId);
            answer.put("optionId", chosen);
            answers.add(answer);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.set("answers", answers);
        return objectMapper.writeValueAsString(payload);
    }
}
