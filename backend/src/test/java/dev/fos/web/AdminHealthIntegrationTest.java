package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.model.AppStart;
import dev.fos.model.HttpStatHourly;
import dev.fos.repo.AppStartRepository;
import dev.fos.repo.HttpStatHourlyRepository;
import dev.fos.service.AccountService;
import dev.fos.service.HttpStatCollector;
import dev.fos.service.HttpStats;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * A seção de saúde do painel, ponta a ponta (#86).
 *
 * <p>Três coisas que só existem passando pela pilha inteira:
 *
 * <ol>
 *   <li><b>Conta comum não entra.</b> A rota vive sob o mesmo {@code OwnerOnlyInterceptor} das
 *       outras de {@code /api/admin/**}, e não inventa checagem própria.
 *   <li><b>A resposta não identifica ninguém</b>, e a asserção é sobre o JSON <em>inteiro</em>:
 *       campo novo com e-mail dentro passaria por qualquer lista de campos esperados.
 *   <li><b>A rota gravada é o padrão do roteamento</b>, e não o caminho que chegou. É a guarda de
 *       privacidade da medição — e a única forma de prová-la é fazendo uma requisição de verdade
 *       por uma rota com segmento variável e olhando o que foi parar na tabela.
 * </ol>
 */
@SpringBootTest(properties = "fos.auth.owner-emails=dono@example.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminHealthIntegrationTest.FixedClockConfig.class)
// `@Transactional`, como as outras integrações desta pasta: sem ele as contas criadas aqui
// sobrevivem no H2 compartilhado e reordenam a listagem de OUTRA classe de teste — foi o que
// aconteceu, e o sintoma apareceu longe daqui.
@Transactional
class AdminHealthIntegrationTest {

    /** Agora são 10h; o período de 24h vai, então, das 11h de ontem às 10h de hoje. */
    private static final Instant AGORA = Instant.parse("2026-08-27T10:20:00Z");

    private static final Instant ESTA_HORA = Instant.parse("2026-08-27T10:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(AGORA, ZoneOffset.UTC);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountService accounts;
    @Autowired private HttpStatHourlyRepository stats;
    @Autowired private AppStartRepository starts;
    @Autowired private HttpStatCollector collector;

    @BeforeEach
    void limpar() {
        collector.flush();
        stats.deleteAll();
        starts.deleteAll();
        accounts.registerLogin("google", "dono", "dono@example.test", true, "Dono");
        accounts.registerLogin("google", "ana", "ana@example.test", true, "Ana");
    }

    @Test
    @DisplayName("conta comum recebe 403, inclusive pelo caminho percent-encoded; sem sessão é 401")
    void aCommonAccountIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/saude").with(as("ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));

        // URI, e não String: o builder de String reencoda o `%` e o teste passaria por engano.
        mockMvc.perform(get(URI.create("/api/%61dmin/saude")).with(as("ana")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/saude")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("nenhum campo da resposta identifica uma pessoa")
    void theResponseNamesNobody() throws Exception {
        plantar(ESTA_HORA, "/api/hoje", 100, 0, 2, 500, 40);

        String corpo =
                mockMvc.perform(get("/api/admin/saude").with(as("dono")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
                        .toLowerCase(Locale.ROOT);

        assertThat(corpo)
                .doesNotContain("@")
                .doesNotContain("userid")
                .doesNotContain("user_id")
                .doesNotContain("visitkey")
                .doesNotContain("ip");
    }

    @Test
    @DisplayName("os três presets são aceitos e qualquer outro período é 400")
    void onlyThePresetsAreAccepted() throws Exception {
        for (int horas : new int[] {24, 72, 168}) {
            mockMvc.perform(
                            get("/api/admin/saude")
                                    .param("horas", String.valueOf(horas))
                                    .with(as("dono")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hours").value(horas))
                    // Uma hora sem linha é ponto na série, não buraco nela.
                    .andExpect(jsonPath("$.hourly.length()").value(horas));
        }

        mockMvc.perform(get("/api/admin/saude").param("horas", "48").with(as("dono")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    @DisplayName("sem medição nenhuma o painel abre, e diz que nada foi coletado ainda")
    void withoutAnyMeasurementItStillOpens() throws Exception {
        mockMvc.perform(get("/api/admin/saude").with(as("dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectedThrough").doesNotExist())
                .andExpect(jsonPath("$.requests").value(0))
                // Zero requisição não é zero de disponibilidade: seria afirmar uma queda que
                // ninguém observou.
                .andExpect(jsonPath("$.availabilityPercent").value(100.0))
                .andExpect(jsonPath("$.p95Ms").value(-1))
                .andExpect(jsonPath("$.routes.length()").value(0));
    }

    @Test
    @DisplayName("disponibilidade, taxa por rota e p95 saem das linhas gravadas")
    void theNumbersComeFromTheRecordedRows() throws Exception {
        // Rota movimentada e sadia; rota quebrada com pouco volume. É a segunda que interessa.
        plantar(ESTA_HORA, "/api/curriculum/tree", 90, 0, 0, 900, 30);
        plantar(ESTA_HORA, "/api/nodes/{code}", 10, 0, 10, 8_000, 900);

        mockMvc.perform(get("/api/admin/saude").with(as("dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests").value(100))
                .andExpect(jsonPath("$.serverErrors").value(10))
                .andExpect(jsonPath("$.availabilityPercent").value(90.0))
                // Ordenado por TAXA: a rota que errou 10 de 10 é a que quebrou, e ela nunca
                // apareceria primeiro num ranking por número absoluto.
                .andExpect(jsonPath("$.routes[0].path").value("/api/nodes/{code}"))
                .andExpect(jsonPath("$.routes[0].errorPercent").value(100.0))
                .andExpect(jsonPath("$.routes.length()").value(1));
    }

    @Test
    @DisplayName("a subida da aplicação aparece no histórico, com horário")
    void theStartupShowsUpInTheHistory() throws Exception {
        starts.save(new AppStart(AGORA.minusSeconds(3_600), "test"));

        mockMvc.perform(get("/api/admin/saude").with(as("dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsInPeriod").value(1))
                .andExpect(jsonPath("$.starts[0].startedAt").exists())
                .andExpect(jsonPath("$.starts[0].profiles").value("test"));
    }

    @Test
    @DisplayName("a rota medida é o padrão do roteamento, nunca o caminho que chegou")
    void whatIsMeasuredIsTheRoutePatternAndNeverTheRawPath() throws Exception {
        // Um código que não existe: o que importa aqui é por qual rota a requisição passou, e o
        // 404 do nó é uma resposta tão medível quanto o 200.
        mockMvc.perform(get("/api/nodes/SEGREDO-123").with(as("dono")));

        collector.flush();

        List<String> rotas = stats.findAll().stream().map(HttpStatHourly::getPath).toList();
        // O segmento variável não pode estar em lugar nenhum da tabela: fosse o caminho cru, um
        // token de confirmação de e-mail acabaria gravado aqui pela mesma porta.
        assertThat(rotas).contains("/api/nodes/{code}").doesNotContain("/api/nodes/SEGREDO-123");
        assertThat(stats.findAll())
                .allSatisfy(linha -> assertThat(linha.getPath()).doesNotContain("SEGREDO"));
    }

    private void plantar(
            Instant hora,
            String path,
            long requisicoes,
            long erros4xx,
            long erros5xx,
            long totalMs,
            long maxMs) {
        long[] histograma = new long[HttpStats.FAIXAS];
        histograma[HttpStats.faixa(Math.max(1, totalMs / Math.max(1, requisicoes)))] = requisicoes;
        stats.save(
                new HttpStatHourly(
                        hora, path, requisicoes, erros4xx, erros5xx, totalMs, maxMs, histograma));
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
