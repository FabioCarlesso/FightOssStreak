package dev.fos.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O app mobile num ambiente sem credencial nenhuma — que é como dev e CI rodam (#139, D68).
 *
 * <p>A regra 4 do {@code CLAUDE.md}: a aplicação sobe sem segredo, e provedor sem credencial não
 * existe. Aqui isso quer dizer 404 nas rotas de login nativo, lista vazia em {@code
 * mobileProviders} e versão mínima nula — e nada disso derruba a subida.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileLoginDesligadoIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("sem credencial, Google e Apple respondem 404 e não aparecem na lista")
    void withoutCredentialsTheNativeLoginsDoNotExist() throws Exception {
        mockMvc.perform(
                        post("/api/mobile/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"idToken\":\"qualquer\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("login_indisponivel"));
        mockMvc.perform(
                        post("/api/mobile/auth/apple")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"identityToken\":\"qualquer\",\"nonce\":\"n\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(jsonPath("$.mobileProviders").isEmpty());
        mockMvc.perform(get("/api/app/versao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumVersion").doesNotExist());
    }

    @Test
    @DisplayName("corpo ausente ou malformado nas rotas do app responde 400, não 500")
    void invalidInputIsAClientError() throws Exception {
        mockMvc.perform(post("/api/mobile/auth/senha").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        mockMvc.perform(
                        post("/api/mobile/auth/google")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
