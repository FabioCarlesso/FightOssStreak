package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fos.model.DrillLog;
import dev.fos.model.SessionKind;
import dev.fos.model.TrainingSession;
import dev.fos.model.UserIdentity;
import dev.fos.repo.DrillLogRepository;
import dev.fos.repo.StreakFreezeRepository;
import dev.fos.repo.TrainingSessionRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.service.AccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * O diário de treino de ponta a ponta (#114, D56/D57/D58).
 *
 * <p>O que estes testes protegem, em uma frase: <b>o diário não pode criar uma segunda verdade</b>.
 * Técnica vinculada tem de ser o mesmo {@code drill_log} do registro pela tela do nó — mesmo SRS,
 * mesmo {@code was_due}, mesmo livro-caixa de freeze —, e o streak tem de continuar sendo uma
 * corrente só, agora alimentada também pelas sessões.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DiaryIntegrationTest.FixedClockConfig.class)
@Transactional
class DiaryIntegrationTest {

    /** Mesmo relógio fixo do {@link ApiIntegrationTest}: streak e SRS são definidos por datas. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        MockMvcBuilderCustomizer authenticatedByDefault() {
            return builder ->
                    builder.defaultRequest(
                            get("/").with(
                                            oauth2Login()
                                                    .clientRegistration(
                                                            ApiIntegrationTest.TEST_REGISTRATION)
                                                    .attributes(
                                                            attributes ->
                                                                    attributes.put(
                                                                            "sub",
                                                                            ApiIntegrationTest
                                                                                    .TEST_SUBJECT)))
                                    .with(csrf()));
        }
    }

    private static final long SEEDED_USER_ID = 1L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserIdentityRepository identities;
    @Autowired private TrainingSessionRepository sessions;
    @Autowired private DrillLogRepository drills;
    @Autowired private StreakFreezeRepository freezes;
    @Autowired private AccountService accounts;

    /**
     * O perdão de dia perdido é gravado em transação PRÓPRIA, então sobrevive ao rollback desta
     * classe — sem esta limpeza o freeze de um teste apareceria como saldo gasto no seguinte.
     */
    @BeforeEach
    void prepare() {
        freezes.deleteAll();
        if (identities
                .findByProviderAndProviderSubject("google", ApiIntegrationTest.TEST_SUBJECT)
                .isEmpty()) {
            identities.save(
                    new UserIdentity(
                            SEEDED_USER_ID,
                            "google",
                            ApiIntegrationTest.TEST_SUBJECT,
                            "autor@example.test",
                            true,
                            "Autor",
                            Instant.parse("2026-08-16T09:00:00Z")));
        }
    }

    @Test
    @DisplayName("só a data basta para registrar um treino, e ele aparece no diário")
    void aDateIsEnough() throws Exception {
        long id = criarSessao("{\"trainedOn\":\"2026-08-16\"}");

        // Sem tipo informado o registro vira AULA — o caso dominante, e o que a tela já marca.
        mockMvc.perform(get("/api/sessoes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainedOn").value("2026-08-16"))
                .andExpect(jsonPath("$.kind").value("AULA"))
                .andExpect(jsonPath("$.countsAsTrainingDay").value(true))
                .andExpect(jsonPath("$.tecnicas.length()").value(0));

        mockMvc.perform(get("/api/sessoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].day").value("2026-08-16"))
                .andExpect(jsonPath("$.days[0].sessions[0].id").value(id))
                .andExpect(jsonPath("$.sessionsInMonth").value(1));
    }

    @Test
    @DisplayName("técnica vinculada vira drill_log com session_id, e passa pelo mesmo SRS")
    void aLinkedTechniqueIsTheSameDrill() throws Exception {
        long id =
                criarSessao(
                        """
                        {"trainedOn":"2026-08-16","kind":"AULA","durationMinutes":90,
                         "feeling":"BEM","weightKg":78.40,"learned":"entrada de raspagem",
                         "improve":"postura na guarda",
                         "tecnicas":[{"nodeCode":"M0.1","recall":"OK","note":"travei no começo"}]}
                        """);

        mockMvc.perform(get("/api/sessoes/" + id))
                .andExpect(jsonPath("$.weightKg").value(78.40))
                .andExpect(jsonPath("$.feeling").value("BEM"))
                .andExpect(jsonPath("$.tecnicas.length()").value(1))
                .andExpect(jsonPath("$.tecnicas[0].nodeCode").value("M0.1"))
                .andExpect(jsonPath("$.tecnicas[0].note").value("travei no começo"));

        // O drill é o mesmo do registro pela tela do nó: entra no histórico do nó e reagenda o SRS.
        mockMvc.perform(get("/api/nodes/M0.1"))
                .andExpect(jsonPath("$.recentDrills[0].drilledOn").value("2026-08-16"))
                .andExpect(jsonPath("$.srs.scheduled").value(true))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        List<DrillLog> gravados = drills.findByUserIdAndSessionIdOrderByIdAsc(SEEDED_USER_ID, id);
        assertThat(gravados).hasSize(1);
        assertThat(gravados.get(0).getSessionId()).isEqualTo(id);
        // `was_due`/`due_on` gravados pelo DrillService, e não reimplementados aqui: o nó não tinha
        // revisão agendada, então o drill não atendeu sugestão nenhuma.
        assertThat(gravados.get(0).isWasDue()).isFalse();
        assertThat(gravados.get(0).getDueOn()).isNull();
    }

    @Test
    @DisplayName("vincular técnica a uma sessão de dia perdoado devolve o freeze")
    void linkingOnAForgivenDayGivesTheFreezeBack() throws Exception {
        drillOn("M0.1", "2026-08-14");
        drillOn("M0.1", "2026-08-16");

        // Faltou o 15: o freeze cobre e a corrente atravessa o buraco (D55).
        mockMvc.perform(get("/api/streak"))
                .andExpect(jsonPath("$.lastFrozenOn").value("2026-08-15"))
                .andExpect(jsonPath("$.freezesRemaining").value(1));

        // A sessão do dia 15 é criada vazia e a técnica é vinculada DEPOIS — o "completa depois"
        // da D56b. O dia deixa de ser dia perdido pelo mesmo caminho do `drilledOn`.
        long id = criarSessao("{\"trainedOn\":\"2026-08-15\",\"kind\":\"DRILL\"}");
        mockMvc.perform(
                        post("/api/sessoes/" + id + "/tecnicas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nodeCode\":\"M0.1\",\"recall\":\"HARD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tecnicas.length()").value(1));

        mockMvc.perform(get("/api/streak"))
                .andExpect(jsonPath("$.currentStreak").value(3))
                .andExpect(jsonPath("$.freezesRemaining").value(2))
                .andExpect(jsonPath("$.lastFrozenOn").doesNotExist());
    }

    @Test
    @DisplayName("sessão sem técnica conta como dia de treino; DESCANSO não conta")
    void restDaysDoNotFeedTheStreak() throws Exception {
        criarSessao("{\"trainedOn\":\"2026-08-16\",\"kind\":\"ROLA\"}");

        // Rola solta não tem nó do currículo, e é justamente o buraco que a D56 foi tapar: sem a
        // sessão, quem treinou hoje veria corrente zerada.
        mockMvc.perform(get("/api/streak"))
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.drilledToday").value(true));

        criarSessao("{\"trainedOn\":\"2026-08-15\",\"kind\":\"DESCANSO\"}");

        // O dia 15 fica no diário e NÃO entra na corrente: sem isso o streak viraria "abri o app".
        mockMvc.perform(get("/api/streak")).andExpect(jsonPath("$.currentStreak").value(1));
        mockMvc.perform(get("/api/sessoes"))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[1].day").value("2026-08-15"))
                .andExpect(jsonPath("$.days[1].countsAsTrainingDay").value(false));
    }

    @Test
    @DisplayName("descanso não recebe técnica: seria dia sem treino com treino registrado")
    void restSessionsRefuseTechniques() throws Exception {
        long id = criarSessao("{\"trainedOn\":\"2026-08-16\",\"kind\":\"DESCANSO\"}");

        mockMvc.perform(
                        post("/api/sessoes/" + id + "/tecnicas")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"nodeCode\":\"M0.1\",\"recall\":\"OK\"}"))
                .andExpect(status().isBadRequest());

        // E o caminho inverso também fecha: sessão com técnica não vira DESCANSO por edição.
        long comTecnica =
                criarSessao(
                        "{\"trainedOn\":\"2026-08-16\",\"kind\":\"AULA\","
                                + "\"tecnicas\":[{\"nodeCode\":\"M0.1\",\"recall\":\"OK\"}]}");
        mockMvc.perform(
                        patch("/api/sessoes/" + comTecnica)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"trainedOn\":\"2026-08-16\",\"kind\":\"DESCANSO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("corrigir a data do treino leva junto a data das técnicas vinculadas")
    void movingTheSessionMovesItsTechniques() throws Exception {
        long id =
                criarSessao(
                        "{\"trainedOn\":\"2026-08-16\",\"kind\":\"AULA\","
                                + "\"tecnicas\":[{\"nodeCode\":\"M0.1\",\"recall\":\"OK\"}]}");

        mockMvc.perform(
                        patch("/api/sessoes/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"trainedOn\":\"2026-08-14\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainedOn").value("2026-08-14"))
                .andExpect(jsonPath("$.tecnicas[0].drilledOn").value("2026-08-14"));

        // O drill vinculado não entra no streak pela própria data — a sessão responde pelo dia
        // dele. Sem a propagação, o `drill_log` continuaria afirmando o dia 16, que some do diário
        // e da corrente: treino que aconteceu e ninguém representa.
        List<DrillLog> vinculados = drills.findByUserIdAndSessionIdOrderByIdAsc(SEEDED_USER_ID, id);
        assertThat(vinculados)
                .singleElement()
                .satisfies(
                        drill ->
                                assertThat(drill.getDrilledOn())
                                        .isEqualTo(LocalDate.of(2026, 8, 14)));

        // O dia 16 deixou de existir para todo mundo ao mesmo tempo, que é o ponto.
        mockMvc.perform(get("/api/sessoes"))
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].day").value("2026-08-14"));
        mockMvc.perform(get("/api/streak")).andExpect(jsonPath("$.drilledToday").value(false));

        // O drill continua sendo o mesmo drill: segue vinculado e segue no histórico do nó, agora
        // pela data corrigida. Correção de data não desfaz nem refaz agendamento de SRS.
        assertThat(vinculados.get(0).getSessionId()).isEqualTo(id);
        mockMvc.perform(get("/api/nodes/M0.1"))
                .andExpect(jsonPath("$.recentDrills[0].drilledOn").value("2026-08-14"));
    }

    @Test
    @DisplayName("o contador do mês conta treinos, e descanso não é treino")
    void theMonthlyCountIgnoresRestDays() throws Exception {
        criarSessao("{\"trainedOn\":\"2026-08-16\",\"kind\":\"AULA\"}");
        criarSessao("{\"trainedOn\":\"2026-08-15\",\"kind\":\"DESCANSO\"}");

        // Dizer "2 treinos registrados" na mesma tela em que o dia 15 aparece marcado como *não
        // conta no streak* seria a UI se contradizendo em dois centímetros de distância.
        mockMvc.perform(get("/api/sessoes"))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.sessionsInMonth").value(1));
    }

    @Test
    @DisplayName("duas sessões no mesmo dia contam um dia de streak")
    void twoSessionsInADayAreOneDay() throws Exception {
        criarSessao("{\"trainedOn\":\"2026-08-16\",\"kind\":\"AULA\"}");
        criarSessao("{\"trainedOn\":\"2026-08-16\",\"kind\":\"FISICO\"}");

        mockMvc.perform(get("/api/streak"))
                .andExpect(jsonPath("$.currentStreak").value(1))
                .andExpect(jsonPath("$.activeDaysLast30").value(1));

        // Manhã e noite são duas sessões — o diário mostra as duas no mesmo dia.
        mockMvc.perform(get("/api/sessoes"))
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].sessions.length()").value(2));
    }

    @Test
    @DisplayName("data futura é recusada, como no registro de drill")
    void futureDatesAreRefused() throws Exception {
        mockMvc.perform(
                        post("/api/sessoes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"trainedOn\":\"2027-01-01\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("drill anterior ao diário aparece como registro avulso, sem migration de dados")
    void olderDrillsShowUpAsStandalone() throws Exception {
        drillOn("M0.1", "2026-08-14");

        mockMvc.perform(get("/api/sessoes"))
                .andExpect(jsonPath("$.days.length()").value(1))
                .andExpect(jsonPath("$.days[0].day").value("2026-08-14"))
                .andExpect(jsonPath("$.days[0].sessions.length()").value(0))
                .andExpect(jsonPath("$.days[0].avulsos.length()").value(1))
                .andExpect(jsonPath("$.days[0].avulsos[0].nodeCode").value("M0.1"))
                .andExpect(jsonPath("$.days[0].countsAsTrainingDay").value(true));
    }

    @Test
    @DisplayName("desvincular devolve o drill ao histórico do nó em vez de apagá-lo")
    void unlinkingKeepsTheDrill() throws Exception {
        long id =
                criarSessao(
                        "{\"trainedOn\":\"2026-08-16\",\"kind\":\"AULA\","
                                + "\"tecnicas\":[{\"nodeCode\":\"M0.1\",\"recall\":\"OK\","
                                + "\"note\":\"o professor corrigiu a base\"}]}");

        mockMvc.perform(delete("/api/sessoes/" + id + "/tecnicas/M0.1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tecnicas.length()").value(0));

        // A nota é histórico (D34): o drill continua no nó, agora como avulso.
        mockMvc.perform(get("/api/nodes/M0.1"))
                .andExpect(jsonPath("$.recentDrills.length()").value(1))
                .andExpect(jsonPath("$.recentDrills[0].note").value("o professor corrigiu a base"));
        assertThat(drills.findByUserIdAndSessionIdOrderByIdAsc(SEEDED_USER_ID, id)).isEmpty();
    }

    @Test
    @DisplayName("editar substitui os campos da sessão e não toca nas técnicas")
    void patchReplacesTheSessionFieldsOnly() throws Exception {
        long id =
                criarSessao(
                        "{\"trainedOn\":\"2026-08-16\",\"kind\":\"AULA\",\"weightKg\":78.00,"
                                + "\"tecnicas\":[{\"nodeCode\":\"M0.1\",\"recall\":\"OK\"}]}");

        // Peso apagado precisa poder ser apagado: campo ausente volta a vazio.
        mockMvc.perform(
                        patch("/api/sessoes/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"trainedOn\":\"2026-08-15\",\"kind\":\"ROLA\","
                                                + "\"learned\":\"passagem por cima\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trainedOn").value("2026-08-15"))
                .andExpect(jsonPath("$.kind").value("ROLA"))
                .andExpect(jsonPath("$.weightKg").doesNotExist())
                .andExpect(jsonPath("$.learned").value("passagem por cima"))
                .andExpect(jsonPath("$.tecnicas.length()").value(1));
    }

    @Test
    @DisplayName("sessão de outra conta responde 404, e não 403")
    void anotherAccountsSessionIsNotFound() throws Exception {
        Long outra =
                accounts.registerLogin("google", "vizinho", "vizinho@example.test", true, "Vizinho")
                        .getId();
        TrainingSession alheia =
                sessions.save(
                        new TrainingSession(
                                outra,
                                LocalDate.of(2026, 8, 16),
                                SessionKind.AULA,
                                Instant.parse("2026-08-16T10:00:00Z")));

        // 404 e não 403: distinguir "não existe" de "não é sua" transformaria a rota em consulta
        // de quais ids existem no banco.
        mockMvc.perform(get("/api/sessoes/" + alheia.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("sessao_nao_encontrada"));
    }

    @Test
    @DisplayName("DELETE /api/me leva as sessões da conta junto")
    void deletingTheAccountTakesTheDiary() throws Exception {
        criarSessao(
                "{\"trainedOn\":\"2026-08-16\",\"kind\":\"AULA\",\"weightKg\":78.00,"
                        + "\"feeling\":\"BEM\",\"learned\":\"raspagem\","
                        + "\"tecnicas\":[{\"nodeCode\":\"M0.1\",\"recall\":\"OK\"}]}");
        assertThat(sessions.findDistinctTrainingDates(SEEDED_USER_ID)).isNotEmpty();

        mockMvc.perform(delete("/api/me")).andExpect(status().isNoContent());

        // Peso e sensação são dado referente à saúde (D57): não sobra linha nenhuma.
        assertThat(sessions.findDistinctTrainingDates(SEEDED_USER_ID)).isEmpty();
        assertThat(drills.findDistinctDrillDates(SEEDED_USER_ID)).isEmpty();
    }

    private long criarSessao(String corpo) throws Exception {
        JsonNode resposta =
                objectMapper.readTree(
                        mockMvc.perform(
                                        post("/api/sessoes")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(corpo))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        return resposta.get("id").asLong();
    }

    /** Registro avulso, pela tela do nó — o caminho que existia antes do diário. */
    private void drillOn(String code, String dia) throws Exception {
        mockMvc.perform(
                        post("/api/nodes/" + code + "/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\",\"drilledOn\":\"" + dia + "\"}"))
                .andExpect(status().isOk());
    }
}
