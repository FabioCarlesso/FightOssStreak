package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.model.UserIdentity;
import dev.fos.repo.GraduationGoalRepository;
import dev.fos.repo.UserIdentityRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Próximo grau de ponta a ponta (#193, D75): aula com ou sem kimono, e a contagem desde o último
 * grau.
 *
 * <p>O que estes testes protegem: a contagem é <b>derivada do diário</b> e conta só {@code AULA}.
 * Rola, drill e físico não inflam o número que a pessoa compara com o mínimo da academia, e aula
 * sem marcação de kimono conta no total sem cair em nenhuma das metades.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GraduationIntegrationTest.FixedClockConfig.class)
@Transactional
class GraduationIntegrationTest {

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
    @Autowired private GraduationGoalRepository metas;

    @BeforeEach
    void prepare() {
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
    @DisplayName("sem meta configurada, meta e contagem vêm nulas")
    void withoutAGoalBothAreNull() throws Exception {
        mockMvc.perform(get("/api/graduacao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal").doesNotExist())
                .andExpect(jsonPath("$.classes").doesNotExist());
    }

    @Test
    @DisplayName("conta só AULA desde o último grau, separada por com e sem kimono")
    void countsOnlyClassesSinceTheLastGraduation() throws Exception {
        // Antes do último grau: conta para o grau que já foi dado, não para o próximo.
        sessao("2026-07-31", "AULA", "GI");
        // No dia do grau: conta, porque a contagem começa inclusiva.
        sessao("2026-08-01", "AULA", "GI");
        sessao("2026-08-05", "AULA", "NOGI");
        // Sem marcação: entra no total e em nenhuma das metades.
        sessao("2026-08-10", "AULA", null);
        // Rola, drill e físico não são aula — inflariam o número comparado com o da academia.
        sessao("2026-08-11", "ROLA", "GI");
        sessao("2026-08-12", "DRILL", "NOGI");
        sessao("2026-08-13", "FISICO", null);
        sessao("2026-08-14", "DESCANSO", null);

        mockMvc.perform(
                        put("/api/graduacao")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"lastGraduationOn":"2026-08-01",
                                         "currentLabel":" branca 1º grau ",
                                         "nextLabel":"branca 2º grau",
                                         "minTotal":80,"minGi":60,"minNogi":20}
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal.lastGraduationOn").value("2026-08-01"))
                .andExpect(jsonPath("$.goal.currentLabel").value("branca 1º grau"))
                .andExpect(jsonPath("$.goal.minTotal").value(80))
                .andExpect(jsonPath("$.classes.total").value(3))
                .andExpect(jsonPath("$.classes.gi").value(1))
                .andExpect(jsonPath("$.classes.nogi").value(1))
                .andExpect(jsonPath("$.classes.unspecified").value(1));

        // A contagem é lida do diário a cada pedido: aula nova aparece sem tocar na meta.
        sessao("2026-08-16", "AULA", "GI");
        mockMvc.perform(get("/api/graduacao"))
                .andExpect(jsonPath("$.classes.total").value(4))
                .andExpect(jsonPath("$.classes.gi").value(2));
    }

    @Test
    @DisplayName("só a data do último grau é obrigatória, e gravar de novo reescreve a meta")
    void onlyTheDateIsRequired() throws Exception {
        gravar("{\"lastGraduationOn\":\"2026-08-01\",\"minTotal\":40}").andExpect(status().isOk());
        gravar("{\"lastGraduationOn\":\"2026-08-10\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal.lastGraduationOn").value("2026-08-10"))
                .andExpect(jsonPath("$.goal.minTotal").doesNotExist())
                .andExpect(jsonPath("$.classes.total").value(0));

        assertThat(metas.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("entrada inválida responde 400, nunca 500")
    void invalidInputIsA400() throws Exception {
        gravar("{\"lastGraduationOn\":\"2026-08-17\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
        gravar("{\"minTotal\":40}").andExpect(status().isBadRequest());
        gravar("{\"lastGraduationOn\":\"2026-08-01\",\"minGi\":-1}")
                .andExpect(status().isBadRequest());
        gravar("{\"lastGraduationOn\":\"2026-08-01\",\"minNogi\":2001}")
                .andExpect(status().isBadRequest());
        gravar("{\"lastGraduationOn\":\"2026-08-01\",\"nextLabel\":\"" + "x".repeat(61) + "\"}")
                .andExpect(status().isBadRequest());
        gravar("{\"lastGraduationOn\":\"ontem\"}").andExpect(status().isBadRequest());
        gravar("{").andExpect(status().isBadRequest());

        assertThat(metas.count()).isZero();
    }

    @Test
    @DisplayName("DELETE tira a meta e deixa o diário intacto")
    void deletingTheGoalKeepsTheDiary() throws Exception {
        sessao("2026-08-05", "AULA", "GI");
        gravar("{\"lastGraduationOn\":\"2026-08-01\"}").andExpect(status().isOk());

        mockMvc.perform(delete("/api/graduacao")).andExpect(status().isNoContent());
        // Duas vezes é o mesmo estado: sem meta.
        mockMvc.perform(delete("/api/graduacao")).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/graduacao")).andExpect(jsonPath("$.goal").doesNotExist());
        mockMvc.perform(get("/api/sessoes")).andExpect(jsonPath("$.sessionsInMonth").value(1));
    }

    @Test
    @DisplayName("DELETE /api/me leva a meta junto")
    void deletingTheAccountTakesTheGoal() throws Exception {
        gravar("{\"lastGraduationOn\":\"2026-08-01\",\"minTotal\":40}").andExpect(status().isOk());
        assertThat(metas.findById(SEEDED_USER_ID)).isPresent();

        mockMvc.perform(delete("/api/me")).andExpect(status().isNoContent());

        assertThat(metas.findById(SEEDED_USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("com ou sem kimono é gravado na sessão, e a edição pode trocar ou apagar")
    void theAttireIsStoredAndEditable() throws Exception {
        long id = sessao("2026-08-16", "AULA", "GI");
        mockMvc.perform(get("/api/sessoes/" + id)).andExpect(jsonPath("$.attire").value("GI"));

        editar(id, "{\"trainedOn\":\"2026-08-16\",\"kind\":\"AULA\",\"attire\":\"NOGI\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attire").value("NOGI"));

        // Campo ausente na edição volta a vazio, como os outros campos da sessão.
        editar(id, "{\"trainedOn\":\"2026-08-16\",\"kind\":\"AULA\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attire").doesNotExist());
    }

    @Test
    @DisplayName("físico e descanso não registram kimono: a API recusa com 400")
    void outsideTheMatThereIsNoAttire() throws Exception {
        criar("{\"trainedOn\":\"2026-08-16\",\"kind\":\"FISICO\",\"attire\":\"GI\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
        criar("{\"trainedOn\":\"2026-08-16\",\"kind\":\"DESCANSO\",\"attire\":\"NOGI\"}")
                .andExpect(status().isBadRequest());
        criar("{\"trainedOn\":\"2026-08-16\",\"kind\":\"AULA\",\"attire\":\"KIMONO\"}")
                .andExpect(status().isBadRequest());

        long id = sessao("2026-08-16", "AULA", "GI");
        editar(id, "{\"trainedOn\":\"2026-08-16\",\"kind\":\"FISICO\",\"attire\":\"GI\"}")
                .andExpect(status().isBadRequest());
        // A sessão recusada continua como estava.
        mockMvc.perform(get("/api/sessoes/" + id))
                .andExpect(jsonPath("$.kind").value("AULA"))
                .andExpect(jsonPath("$.attire").value("GI"));
    }

    private long sessao(String dia, String tipo, String kimono) throws Exception {
        String attire = kimono == null ? "" : ",\"attire\":\"" + kimono + "\"";
        String corpo =
                criar("{\"trainedOn\":\"" + dia + "\",\"kind\":\"" + tipo + "\"" + attire + "}")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(corpo).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions criar(String corpo)
            throws Exception {
        return mockMvc.perform(
                post("/api/sessoes").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private org.springframework.test.web.servlet.ResultActions editar(long id, String corpo)
            throws Exception {
        return mockMvc.perform(
                patch("/api/sessoes/" + id).contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private org.springframework.test.web.servlet.ResultActions gravar(String corpo)
            throws Exception {
        return mockMvc.perform(
                put("/api/graduacao").contentType(MediaType.APPLICATION_JSON).content(corpo));
    }
}
