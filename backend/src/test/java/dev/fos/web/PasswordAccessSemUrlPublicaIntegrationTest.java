package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credencial de envio sem {@code fos.public-url} utilizável (FOS-02).
 *
 * <p>É a configuração pela metade que ninguém percebe: o envio existe, e o que falta é a única
 * origem da qual o link pode sair. Antes, a requisição preenchia essa lacuna — com o {@code Host}
 * que quem chamou escolheu. Aqui a porta simplesmente não existe, como sem credencial de envio.
 *
 * <p>O valor configurado é {@code http://} fora de localhost, e não vazio, para cobrir as duas
 * coisas de uma vez: valor inválido vale como ausente.
 */
@SpringBootTest(properties = "fos.public-url=http://fos.example.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PasswordAccessIntegrationTest.CaixaDeSaida.class)
@Transactional
class PasswordAccessSemUrlPublicaIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void limpar() {
        PasswordAccessIntegrationTest.CaixaDeSaida.ENVIADOS.clear();
    }

    @Test
    @DisplayName("cadastro e recuperação respondem 503 e não mandam e-mail nenhum")
    void noPublicUrlMeansNoEmailedLink() throws Exception {
        mockMvc.perform(
                        post("/api/auth/cadastro")
                                .with(csrf())
                                .header("X-Forwarded-Host", "evil.test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"aluno@example.test\","
                                                + "\"senha\":\"tatame-quarta-feira\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("cadastro_indisponivel"));
        mockMvc.perform(
                        post("/api/auth/senha/esquecida")
                                .with(csrf())
                                .header("X-Forwarded-Host", "evil.test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"aluno@example.test\"}"))
                .andExpect(status().isServiceUnavailable());

        assertThat(PasswordAccessIntegrationTest.CaixaDeSaida.ENVIADOS).isEmpty();
    }

    @Test
    @DisplayName("a tela de login não oferece o cadastro por senha")
    void signUpIsNotOffered() throws Exception {
        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordEnabled").value(false));
    }
}
