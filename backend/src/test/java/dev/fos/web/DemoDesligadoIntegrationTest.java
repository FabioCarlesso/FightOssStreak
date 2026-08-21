package dev.fos.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sem conta-modelo configurada, o acesso demonstrativo simplesmente não existe (#62).
 *
 * <p>Este é o ambiente de dev e o do CI — nenhum segredo, nenhuma configuração —, e o que se afirma
 * aqui é que ele sobe igual. Mesma regra dos provedores de login (D36) e do envio de e-mail (D37):
 * recurso sem configuração não aparece na tela, em vez de aparecer e falhar no clique.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoDesligadoIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("a landing não recebe o botão de demonstração")
    void theLandingIsNotOfferedTheDemo() throws Exception {
        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoEnabled").value(false));
    }

    @Test
    @DisplayName("o endpoint responde 404, e não erro de servidor")
    void theEndpointSaysItDoesNotExistHere() throws Exception {
        mockMvc.perform(post("/api/demo/sessao").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("demo_indisponivel"));
    }
}
