package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.fos.model.QuizQuestion;
import dev.fos.repo.QuizQuestionRepository;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Percorre o fluxo real do MVP contra a aplicação inteira: sobe, ingere o currículo versionado,
 * lê a árvore, responde o quiz, registra drill e confere a agenda de revisão.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ApiIntegrationTest.FixedClockConfig.class)
@Transactional
class ApiIntegrationTest {

    /** Relógio fixo: streak e SRS são definidos por datas, então "hoje" precisa ser determinístico. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

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
    @DisplayName("nó ainda sem vídeo catalogado é servido sem quebrar")
    void uncataloguedVideoIsNormalState() throws Exception {
        // M2 em diante ainda não passou por curadoria — o nó tem que abrir mesmo assim.
        mockMvc.perform(get("/api/nodes/M2.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.video.catalogued").value(false))
                .andExpect(jsonPath("$.video.youtubeId").doesNotExist());
    }

    @Test
    @DisplayName("nó com vídeo catalogado traz embed sem cookie, link do watch e crédito ao canal")
    void cataloguedVideoIsServedWithCredit() throws Exception {
        JsonNode video = getJson("/api/nodes/M0.1").get("video");

        assertThat(video.get("catalogued").asBoolean()).isTrue();
        assertThat(video.get("youtubeId").asText()).matches("[\\w-]{11}");
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
    @DisplayName("acertar o quiz conclui o nó e desbloqueia o sucessor")
    void passingQuizUnlocksSuccessor() throws Exception {
        completeNode("M0.3");
        completeNode("M0.4");

        JsonNode tree = getJson("/api/curriculum/tree");
        assertThat(statusOf(tree, "M0.3")).isEqualTo("COMPLETED");
        assertThat(statusOf(tree, "M1.1"))
                .as("M1.1 depende de M0.3 e M0.4")
                .isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("errar o quiz devolve explicações e não conclui o nó")
    void failingQuizExplainsWithoutCompleting() throws Exception {
        JsonNode node = getJson("/api/nodes/M0.1");
        String payload = answerPayload(node, false);

        JsonNode result = objectMapper.readTree(mockMvc
                .perform(post("/api/nodes/M0.1/quiz")
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

        mockMvc.perform(post("/api/nodes/M0.1/drill")
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
        mockMvc.perform(post("/api/nodes/M0.1/drill")
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
        mockMvc.perform(post("/api/nodes/M0.1/drill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recall\":\"OK\",\"drilledOn\":\"2027-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    @DisplayName("nó sem quiz escrito responde 409, não erro de servidor")
    void nodeWithoutQuizReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/nodes/M8.3/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[{\"questionId\":1,\"optionId\":1}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("quiz_unavailable"));
    }

    @Test
    @DisplayName("nó sem quiz é concluído pelo registro de drill")
    void nodeWithoutQuizIsCompletedByDrill() throws Exception {
        mockMvc.perform(post("/api/nodes/M2.2/drill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
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

        mockMvc.perform(post("/api/disclaimer/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":\"test-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true));
    }

    @Test
    @DisplayName("aceitar uma versão antiga do aviso é recusado")
    void staleDisclaimerVersionIsRejected() throws Exception {
        mockMvc.perform(post("/api/disclaimer/accept")
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

        mockMvc.perform(post("/api/nodes/M0.1/drill")
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

        // Drill retroativo com recall ruim: o nó é reagendado para o passado e passa a estar vencido.
        // Neste registro ele ainda não estava vencido — a revisão era para 17/08.
        mockMvc.perform(post("/api/nodes/M0.1/drill")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recall\":\"FORGOT\",\"drilledOn\":\"2026-08-10\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/metrics/mvp"))
                .andExpect(jsonPath("$.srsAdherence.attended").value(0))
                .andExpect(jsonPath("$.srsAdherence.scheduled").value(1))
                .andExpect(jsonPath("$.srsAdherence.percent").value(0));

        // Agora sim: o nó está vencido e o drill de hoje atende à sugestão da agenda.
        mockMvc.perform(post("/api/nodes/M0.1/drill")
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
        mockMvc.perform(post("/api/nodes/M0.1/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerPayload(node, false)))
                .andExpect(status().isOk());
        completeNode("M0.1");

        mockMvc.perform(get("/api/metrics/mvp")).andExpect(jsonPath("$.quizRetakes.value").value(0));

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
        mockMvc.perform(post("/api/nodes/" + code + "/quiz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerPayload(node, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passed").value(true));
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
        return objectMapper.readTree(mockMvc
                .perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    /**
     * Monta respostas para o quiz.
     *
     * <p>O gabarito não vem na API — de propósito, e a ordem das alternativas é embaralhada. Então o
     * teste consulta o repositório para saber qual é a correta, em vez de assumir posição.
     */
    private String answerPayload(JsonNode node, boolean correct) throws Exception {
        ArrayNode answers = objectMapper.createArrayNode();
        for (JsonNode question : node.get("quiz")) {
            long questionId = question.get("id").asLong();
            QuizQuestion stored = quizQuestionRepository.findById(questionId).orElseThrow();
            long correctId = stored.correctOption().getId();

            long chosen = correct
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
