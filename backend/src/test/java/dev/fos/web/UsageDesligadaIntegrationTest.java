package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.repo.UsageEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code fos.usage.enabled=false} — a saída de quem sobe este código e não quer coleta nenhuma.
 *
 * <p>Desligar precisa significar <b>desligado</b>, e não "grava nada mas continua sendo chamado":
 * sem o 503 com código, o navegador seguiria mandando uma requisição por navegação, para sempre,
 * para um servidor que as joga fora. Quem paga por requisição e por banco em plano pequeno paga por
 * isso.
 *
 * <p>O <b>código</b> é o que este teste protege, e não o status: 503 sem código é o que o proxy
 * devolve enquanto o backend reinicia, e aquele não pode desligar a coleta de quem está navegando.
 */
@SpringBootTest(properties = "fos.usage.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsageDesligadaIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsageEventRepository events;

    @Test
    @DisplayName("com a coleta desligada, o endpoint se anuncia em 503 e nada é gravado")
    void desligadaSeAnuncia() throws Exception {
        events.deleteAll();

        mockMvc.perform(
                        post("/api/telemetria/evento")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("User-Agent", "qualquer")
                                .content("{\"caminho\":\"/\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(UsageController.COLETA_DESLIGADA));

        assertThat(events.count()).isZero();
    }
}
