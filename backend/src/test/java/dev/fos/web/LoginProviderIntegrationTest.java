package dev.fos.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O caminho que só existe com credencial configurada.
 *
 * <p>O {@link AuthIntegrationTest} cobre a subida <em>sem</em> nenhum provedor, que é o cenário de
 * dev e do CI. Aqui está o outro lado, e ele tem dois detalhes que quebram calados em produção: as
 * credenciais precisam mesmo chegar da configuração até virar um {@code ClientRegistration}, e o
 * fluxo OAuth precisa viver sob {@code /api} — os defaults do Spring cairiam fora do único {@code
 * location} que o nginx encaminha ao backend (D23/D24), e o login só falharia no ambiente real.
 */
@SpringBootTest(
        properties = {
            "fos.auth.providers.google.client-id=id-de-teste",
            "fos.auth.providers.google.client-secret=segredo-de-teste"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginProviderIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("provedor configurado aparece na tela de login; o não configurado não")
    void configuredProviderIsOffered() throws Exception {
        mockMvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.length()").value(1))
                .andExpect(jsonPath("$.providers[0].id").value("google"))
                .andExpect(jsonPath("$.providers[0].label").value("Google"))
                .andExpect(
                        jsonPath("$.providers[0].authorizationUrl")
                                .value("/api/oauth2/authorization/google"));
    }

    @Test
    @DisplayName("o fluxo de autorização vive sob /api e volta para o redirect sob /api")
    void authorizationFlowStaysUnderApi() throws Exception {
        mockMvc.perform(get("/api/oauth2/authorization/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        header().string(
                                        "Location",
                                        Matchers.containsString(
                                                "accounts.google.com/o/oauth2/v2/auth")))
                // O redirect_uri é o que o provedor confere contra o cadastrado. Fora de /api, o
                // nginx não encaminharia a volta do login para o backend.
                .andExpect(
                        header().string(
                                        "Location",
                                        Matchers.containsString(
                                                "redirect_uri=http://localhost/api/login/oauth2/code/google")));
    }

    @Test
    @DisplayName("o default do Spring para o fluxo OAuth não responde")
    void springDefaultsAreNotExposed() throws Exception {
        // Se um dia isto passar a redirecionar, o fluxo saiu de /api sem ninguém notar — e só
        // quebraria atrás do nginx.
        mockMvc.perform(get("/oauth2/authorization/google")).andExpect(status().is4xxClientError());
    }
}
