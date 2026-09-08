package dev.fos.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.service.AccountService;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O identificador de correlação do 500 (#86).
 *
 * <p>Ele existe para uma situação concreta: alguém diz "deu erro ao salvar" e o log tem um dia
 * inteiro de linhas. Com o código na tela, o relato vira uma busca; sem ele, vira adivinhação.
 *
 * <p>E o teste guarda também o que o {@code @ExceptionHandler(Exception.class)} <b>não</b> pode
 * fazer. Ele tem precedência sobre o resolvedor padrão do Spring, então sem cuidado o JSON
 * malformado e o método HTTP errado — hoje 400 e 405 — virariam 500. Isso não seria só resposta
 * errada: cada um deles entraria na taxa de erro que dispara o alerta desta mesma issue, e o
 * monitoramento passaria a avisar sobre requisição malfeita de quem chama.
 *
 * <p>O <b>parâmetro com tipo errado</b> entrou aqui na #102 porque de fato escapou: o desvio de 4xx
 * reconhece quem implementa {@code ErrorResponse}, e {@code TypeMismatchException} não implementa.
 * As três rotas com parâmetro tipado respondiam 500 a {@code ?dias=abc}. É o mesmo risco desta
 * classe, e por isso mora nela — não junto da feature que o revelou.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ErroInternoIntegrationTest.RotaQueExplode.class)
// `@Transactional`, como as outras integrações desta pasta: sem ele as contas criadas aqui
// sobrevivem no H2 compartilhado e reordenam a listagem de OUTRA classe de teste — foi o que
// aconteceu, e o sintoma apareceu longe daqui.
@Transactional
class ErroInternoIntegrationTest {

    /** Uma rota que estoura de propósito. Só existe no contexto deste teste. */
    @TestConfiguration
    @RestController
    static class RotaQueExplode {

        @GetMapping("/api/teste/explode")
        String explode() {
            throw new IllegalStateException("segredo-de-configuracao-que-nao-pode-vazar");
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountService accounts;

    @BeforeEach
    void conta() {
        accounts.registerLogin("google", "ana", "ana@example.test", true, "Ana");
    }

    @Test
    @DisplayName("o 500 traz um identificador de correlação, e não a mensagem da exceção")
    void theInternalErrorCarriesACorrelationId() throws Exception {
        mockMvc.perform(get("/api/teste/explode").with(as("ana")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("erro_interno"))
                // Oito caracteres de um alfabeto sem os pares que se confundem à mão: ele é feito
                // para ser copiado de um print ou lido em voz alta.
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(Matchers.matchesPattern("[23456789A-HJ-NP-Z]{8}")))
                // A mensagem da exceção fica no log, que é de quem opera: erro não previsto é
                // justamente aquele cujo texto ninguém revisou, e ele pode carregar configuração.
                .andExpect(
                        jsonPath("$.message")
                                .value(Matchers.not(Matchers.containsString("segredo"))))
                // O código também vai na mensagem, para quem só consegue mandar um print da tela.
                .andExpect(
                        jsonPath("$.message").value(Matchers.containsString("informe o código")));
    }

    @Test
    @DisplayName("erro esperado continua sem identificador: ele só aparece onde é útil")
    void anExpectedErrorHasNoCorrelationId() throws Exception {
        mockMvc.perform(get("/api/nodes/NAO-EXISTE").with(as("ana")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").doesNotExist());
    }

    @Test
    @DisplayName("o método HTTP errado continua sendo 4xx, e não vira incidente")
    void aWrongMethodIsStillAClientError() throws Exception {
        mockMvc.perform(post("/api/teste/explode").with(as("ana")).with(csrf()))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.correlationId").doesNotExist());
    }

    @Test
    @DisplayName("parâmetro com tipo errado é 400, e não incidente — nas três rotas que o aceitam")
    void aTypeMismatchIsAClientError() throws Exception {
        // O defeito que este teste fecha: `TypeMismatchException` não implementa `ErrorResponse`,
        // então escapava do desvio de 4xx do catch-all e virava 500 — 5xx da própria aplicação
        // entrando na taxa que dispara o alerta de incidente da D54. Um crawler com `?dias=abc`
        // podia gerar e-mail de "site fora do ar".
        for (String rota :
                List.of(
                        "/api/streak/historico?dias=abc",
                        "/api/sessoes?limite=abc",
                        "/api/sessoes?de=naoehdata")) {
            mockMvc.perform(get(rota).with(as("ana")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("invalid_request"))
                    // Sem identificador: 400 é resposta esperada, e correlação só existe no 500.
                    .andExpect(jsonPath("$.correlationId").doesNotExist());
        }
    }

    @Test
    @DisplayName("o 400 diz qual parâmetro está errado, sem devolver o valor recusado")
    void theBadRequestNamesTheParameterWithoutEchoingTheValue() throws Exception {
        mockMvc.perform(get("/api/streak/historico?dias=<script>").with(as("ana")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(Matchers.containsString("dias")))
                // O valor é texto de quem chamou: devolvê-lo seria refleti-lo. E a mensagem do
                // Spring ainda nomearia `java.lang.Integer`, que não é assunto de quem chama.
                .andExpect(
                        jsonPath("$.message")
                                .value(Matchers.not(Matchers.containsString("script"))))
                .andExpect(
                        jsonPath("$.message")
                                .value(Matchers.not(Matchers.containsString("java.lang"))));
    }

    /** Sessão do par (provedor, subject) — o mesmo que o {@code CurrentUserProvider} lê. */
    private static RequestPostProcessor as(String subject) {
        return oauth2Login()
                .clientRegistration(
                        ClientRegistration.withRegistrationId("google")
                                .clientId("test")
                                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                                .redirectUri("{baseUrl}/api/login/oauth2/code/google")
                                .authorizationUri("https://example.test/authorize")
                                .tokenUri("https://example.test/token")
                                .userInfoUri("https://example.test/userinfo")
                                .userNameAttributeName("sub")
                                .build())
                .attributes(attrs -> attrs.put("sub", subject));
    }
}
