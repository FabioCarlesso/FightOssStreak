package dev.fos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Todo verbo que a API declara precisa passar no preflight de CORS (#114).
 *
 * <p>Existe porque a falha é silenciosa e assimétrica: em produção web e API são a mesma origem,
 * então nem chega a ser requisição CORS e a lista de métodos nunca é consultada. Atrás do proxy do
 * Vite o navegador manda a origem de {@code :5173}, e um verbo ausente volta {@code 403 "Invalid
 * CORS request"} — resposta do filtro, antes do {@code ApiExceptionHandler}, sem corpo que explique
 * nada. Nem o teste de MockMvc comum nem o de UI em jsdom passam por aí: o primeiro não manda
 * origem cruzada, o segundo usa {@code fetch} de mentira.
 *
 * <p>Foi exatamente o que aconteceu com o {@code PATCH} do diário: a rota existia, os testes
 * passavam, e o botão <i>Salvar</i> não salvava em nenhuma máquina de desenvolvimento. Rota nova
 * com verbo novo passa a reprovar aqui, e não na tela de alguém.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsMetodosTest {

    /** A origem que o dev server do Vite carimba — a única que o {@code SecurityConfig} aceita. */
    private static final String ORIGEM_DO_VITE = "http://localhost:5173";

    @Autowired private MockMvc mockMvc;

    // Qualificado porque o Actuator registra o seu: sem isto o contexto tem dois.
    @Autowired
    @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("nenhum verbo declarado sob /api é recusado no preflight")
    void everyDeclaredMethodSurvivesPreflight() throws Exception {
        Set<String> verbos = verbosDeclaradosSobApi();
        assertThat(verbos).as("a API declara algum verbo").isNotEmpty();

        for (String verbo : verbos) {
            int status =
                    mockMvc.perform(
                                    options("/api/sessoes/1")
                                            .header("Origin", ORIGEM_DO_VITE)
                                            .header("Access-Control-Request-Method", verbo))
                            .andReturn()
                            .getResponse()
                            .getStatus();

            assertThat(status)
                    .as(
                            "%s é usado por um controlador e o preflight o recusou:"
                                    + " acrescente-o a SecurityConfig.corsConfigurationSource()",
                            verbo)
                    .isEqualTo(200);
        }
    }

    /** Os verbos que os {@code @RequestMapping} sob {@code /api} realmente declaram. */
    private Set<String> verbosDeclaradosSobApi() {
        Set<String> verbos = new TreeSet<>();
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            boolean daApi =
                    info.getPatternValues().stream().anyMatch(padrao -> padrao.startsWith("/api"));
            if (daApi) {
                info.getMethodsCondition()
                        .getMethods()
                        .forEach(metodo -> verbos.add(metodo.name()));
            }
        }
        return verbos;
    }
}
