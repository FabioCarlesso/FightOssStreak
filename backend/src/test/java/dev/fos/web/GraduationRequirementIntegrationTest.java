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

import dev.fos.model.AppUser;
import dev.fos.model.GraduationRequirement;
import dev.fos.model.ProgressStatus;
import dev.fos.model.UserIdentity;
import dev.fos.model.UserNodeKey;
import dev.fos.model.UserProgress;
import dev.fos.repo.AppUserRepository;
import dev.fos.repo.GraduationChecklistRepository;
import dev.fos.repo.GraduationRequirementRepository;
import dev.fos.repo.NodeRepository;
import dev.fos.repo.UserIdentityRepository;
import dev.fos.repo.UserProgressRepository;
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
 * Requisitos do próximo grau como checklist pessoal, de ponta a ponta (#196, D78).
 *
 * <p>O que estes testes protegem: o texto colado vira requisitos limpos e com teto; o status de
 * requisito ligado a nós muda sozinho com o progresso; e a checklist é da conta e de mais ninguém —
 * outra conta não lê, não altera e não apaga, e {@code DELETE /api/me} leva tudo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(GraduationRequirementIntegrationTest.FixedClockConfig.class)
@Transactional
class GraduationRequirementIntegrationTest {

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
    @Autowired private GraduationRequirementRepository requisitos;
    @Autowired private GraduationChecklistRepository checklists;
    @Autowired private AppUserRepository contas;
    @Autowired private NodeRepository nos;
    @Autowired private UserProgressRepository progresso;

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
    @DisplayName("colar o texto cria um requisito por linha, sem linhas vazias e sem marcadores")
    void pastingCreatesCleanRequirements() throws Exception {
        importar(
                        "branca 2º grau",
                        "• Fuga de montada\n\n  - 2. Armlock da guarda\n1) Guarda fechada\n"
                                + "100kg: manutenção\n(3)   Tempo   de faixa\na) Comportamento")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("branca 2º grau"))
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.done").value(0))
                .andExpect(jsonPath("$.items[0].text").value("Fuga de montada"))
                .andExpect(jsonPath("$.items[1].text").value("Armlock da guarda"))
                .andExpect(jsonPath("$.items[2].text").value("Guarda fechada"))
                // Número sem pontuação não é marcador: "100kg" é texto.
                .andExpect(jsonPath("$.items[3].text").value("100kg: manutenção"))
                .andExpect(jsonPath("$.items[4].text").value("Tempo de faixa"))
                .andExpect(jsonPath("$.items[5].text").value("Comportamento"))
                .andExpect(jsonPath("$.items[0].status").value("PENDENTE"));

        // Colar de novo acrescenta ao fim, e não substitui: não pode apagar vínculo já confirmado.
        importar("", "Raspagem de tesoura")
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.label").value("branca 2º grau"))
                .andExpect(jsonPath("$.items[6].text").value("Raspagem de tesoura"));
    }

    @Test
    @DisplayName("texto acima do teto responde 400 com mensagem clara, e não 500")
    void overTheCapIsA400() throws Exception {
        importar("", "x".repeat(201))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("linha 1")));

        String sessentaEUm =
                java.util.stream.IntStream.rangeClosed(1, 61)
                        .mapToObj(i -> "Requisito " + i)
                        .collect(java.util.stream.Collectors.joining("\n"));
        importar("", sessentaEUm)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value(org.hamcrest.Matchers.containsString("até 60")));

        importar("", "\n  \n - \n").andExpect(status().isBadRequest());
        importar("", "y".repeat(8001)).andExpect(status().isBadRequest());
        assertThat(requisitos.countByUserId(SEEDED_USER_ID)).isZero();
    }

    @Test
    @DisplayName("o status do requisito ligado a nós muda sozinho com o progresso deles")
    void statusFollowsNodeProgress() throws Exception {
        long id = primeiroId(importar("", "Etiqueta e quedas"));

        // A ligação só existe depois de confirmada: importar não liga nada.
        mockMvc.perform(get("/api/graduacao/requisitos"))
                .andExpect(jsonPath("$.items[0].nodes").isEmpty())
                .andExpect(jsonPath("$.nodeCodes").isEmpty());

        editar(id, "{\"nodeCodes\":[\"m0.1\",\"M0.2\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].nodes[0].code").value("M0.1"))
                .andExpect(jsonPath("$.items[0].nodes[1].code").value("M0.2"))
                .andExpect(jsonPath("$.items[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$.nodeCodes[0]").value("M0.1"));

        // Drill pela tela do nó: nó com quiz vai para IN_PROGRESS, e o requisito, para em
        // andamento.
        mockMvc.perform(
                        post("/api/nodes/M0.1/drill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"recall\":\"OK\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/graduacao/requisitos"))
                .andExpect(jsonPath("$.items[0].status").value("EM_ANDAMENTO"));

        // Os dois concluídos (como o quiz faria): o requisito conclui sem ação na checklist.
        concluir("M0.1");
        concluir("M0.2");
        mockMvc.perform(get("/api/graduacao/requisitos"))
                .andExpect(jsonPath("$.items[0].status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.done").value(1));

        // A marcação à mão não vale para requisito com nó: o status é do progresso.
        editar(id, "{\"manualDone\":false,\"nodeCodes\":[\"M0.1\",\"M0.3\"]}")
                .andExpect(jsonPath("$.items[0].status").value("EM_ANDAMENTO"));
    }

    @Test
    @DisplayName("requisito sem nó se marca à mão; nó inexistente responde 400")
    void requirementWithoutNodesIsMarkedByHand() throws Exception {
        long id = primeiroId(importar("", "Tempo mínimo de faixa"));

        editar(id, "{\"manualDone\":true}")
                .andExpect(jsonPath("$.items[0].status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.items[0].manualDone").value(true));

        editar(id, "{\"nodeCodes\":[\"M99.9\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nó inexistente: M99.9"));
        editar(id, "{\"text\":\"   \"}").andExpect(status().isBadRequest());
        editar(id, "{\"text\":\"Tempo de faixa: seis meses\"}")
                .andExpect(jsonPath("$.items[0].text").value("Tempo de faixa: seis meses"));
    }

    @Test
    @DisplayName("editar normaliza o espaço como colar: sem quebra de linha e sem NBSP")
    void editingNormalizesSpaces() throws Exception {
        long id = primeiroId(importar("", "Kimura"));

        editar(id, "{\"text\":\" Kimura\\nda\\u00A0guarda\\tfechada \"}")
                .andExpect(jsonPath("$.items[0].text").value("Kimura da guarda fechada"));
        editar(id, "{\"text\":\"\\u00A0\\n\"}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("ligar nós apaga a marcação à mão: desligar depois não conclui sozinho")
    void linkingNodesClearsTheManualMark() throws Exception {
        long id = primeiroId(importar("", "Fuga de montada"));
        editar(id, "{\"manualDone\":true}")
                .andExpect(jsonPath("$.items[0].status").value("CONCLUIDO"));

        editar(id, "{\"nodeCodes\":[\"M0.1\"]}")
                .andExpect(jsonPath("$.items[0].manualDone").value(false));
        editar(id, "{\"manualDone\":true}")
                .andExpect(jsonPath("$.items[0].manualDone").value(false));

        editar(id, "{\"nodeCodes\":[]}")
                .andExpect(jsonPath("$.items[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$.items[0].manualDone").value(false));
    }

    @Test
    @DisplayName("reordenar exige todos os ids da checklist, cada um uma vez")
    void reorderNeedsEveryIdOnce() throws Exception {
        String corpo =
                importar("", "Primeiro\nSegundo\nTerceiro")
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var itens = objectMapper.readTree(corpo).get("items");
        long a = itens.get(0).get("id").asLong();
        long b = itens.get(1).get("id").asLong();
        long c = itens.get(2).get("id").asLong();

        ordenar("[" + c + "," + a + "]").andExpect(status().isBadRequest());
        ordenar("[" + c + "," + a + "," + a + "]").andExpect(status().isBadRequest());
        ordenar("[" + c + "," + a + "," + b + "]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].text").value("Terceiro"))
                .andExpect(jsonPath("$.items[1].text").value("Primeiro"))
                .andExpect(jsonPath("$.items[2].text").value("Segundo"));
    }

    @Test
    @DisplayName("uma conta não lê, não altera nem apaga a checklist de outra")
    void anotherAccountsChecklistIsInvisible() throws Exception {
        Long outra =
                contas.save(AppUser.approved("outra", Instant.parse("2026-08-16T09:00:00Z")))
                        .getId();
        GraduationRequirement alheio =
                requisitos.save(
                        new GraduationRequirement(
                                outra, 0, "Requisito de outra conta", Instant.now()));

        mockMvc.perform(get("/api/graduacao/requisitos")).andExpect(jsonPath("$.total").value(0));
        editar(alheio.getId(), "{\"manualDone\":true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("requisito_nao_encontrado"));
        mockMvc.perform(delete("/api/graduacao/requisitos/" + alheio.getId()))
                .andExpect(status().isNotFound());
        // Reordenar com o id alheio também não passa: não é da checklist de quem chama.
        ordenar("[" + alheio.getId() + "]").andExpect(status().isBadRequest());

        assertThat(requisitos.findById(alheio.getId()))
                .get()
                .extracting(GraduationRequirement::isManualDone)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("apagar um requisito, a checklist inteira e a conta")
    void deletingRequirementsAndTheAccount() throws Exception {
        long id = primeiroId(importar("branca 1º grau", "Um\nDois"));
        editar(id, "{\"nodeCodes\":[\"M0.1\"]}").andExpect(status().isOk());

        mockMvc.perform(delete("/api/graduacao/requisitos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
        mockMvc.perform(delete("/api/graduacao/requisitos")).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/graduacao/requisitos"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.label").doesNotExist());

        importar("branca 1º grau", "Três").andExpect(status().isOk());
        mockMvc.perform(delete("/api/me")).andExpect(status().isNoContent());
        assertThat(requisitos.countByUserId(SEEDED_USER_ID)).isZero();
        assertThat(checklists.findById(SEEDED_USER_ID)).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions importar(String label, String texto)
            throws Exception {
        return mockMvc.perform(
                post("/api/graduacao/requisitos/importar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                objectMapper.writeValueAsString(
                                        java.util.Map.of("label", label, "text", texto))));
    }

    private org.springframework.test.web.servlet.ResultActions editar(long id, String corpo)
            throws Exception {
        return mockMvc.perform(
                patch("/api/graduacao/requisitos/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo));
    }

    private org.springframework.test.web.servlet.ResultActions ordenar(String ids)
            throws Exception {
        return mockMvc.perform(
                put("/api/graduacao/requisitos/ordem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":" + ids + "}"));
    }

    private long primeiroId(org.springframework.test.web.servlet.ResultActions resultado)
            throws Exception {
        String corpo = resultado.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(corpo).get("items").get(0).get("id").asLong();
    }

    private void concluir(String codigo) {
        Long nodeId = nos.findByCode(codigo).orElseThrow().getId();
        UserNodeKey chave = new UserNodeKey(SEEDED_USER_ID, nodeId);
        UserProgress registro =
                progresso
                        .findById(chave)
                        .orElseGet(
                                () ->
                                        new UserProgress(
                                                chave,
                                                ProgressStatus.COMPLETED,
                                                Instant.parse("2026-08-16T10:00:00Z")));
        registro.setStatus(ProgressStatus.COMPLETED);
        progresso.save(registro);
    }
}
