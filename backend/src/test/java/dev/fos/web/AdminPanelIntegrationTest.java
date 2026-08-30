package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.model.UsageDaily;
import dev.fos.model.UsageDimension;
import dev.fos.model.UsageEventType;
import dev.fos.repo.UsageDailyRepository;
import dev.fos.repo.UsageEventRepository;
import dev.fos.service.AccountService;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * O painel do administrador, ponta a ponta (#85, D50).
 *
 * <p>Três coisas são provadas aqui, e nenhuma delas é "o número está certo" — essa é a conta que o
 * {@code UsagePanelServiceTest} faz. Estas são as que só existem passando pela pilha inteira:
 *
 * <ol>
 *   <li><b>Conta comum não entra</b>, nem pelo caminho percent-encoded que a D36 registra como
 *       defeito real de autorização. O painel não inventa checagem própria: ele entra sob o mesmo
 *       {@code OwnerOnlyInterceptor} das outras rotas de {@code /api/admin/**}.
 *   <li><b>A resposta não identifica ninguém.</b> A asserção é sobre o JSON <em>inteiro</em>, e não
 *       sobre campo por campo: campo novo com e-mail dentro passaria por qualquer lista de campos
 *       esperados, e é justamente o acréscimo distraído que se quer barrar.
 *   <li><b>Os seis degraus vêm sempre</b>, inclusive os zerados — degrau que some esconde onde as
 *       pessoas desistem.
 * </ol>
 */
@SpringBootTest(properties = "fos.auth.owner-emails=dono@example.test")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AdminPanelIntegrationTest.FixedClockConfig.class)
@Transactional
class AdminPanelIntegrationTest {

    /** Hoje é 2026-08-27, então o período de 7 dias vai de 20/08 a 26/08. */
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 27);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AccountService accounts;
    @Autowired private UsageDailyRepository daily;
    @Autowired private UsageEventRepository events;

    @BeforeEach
    void limpar() {
        daily.deleteAll();
        events.deleteAll();
        accounts.registerLogin("google", "dono", "dono@example.test", true, "Dono");
        accounts.registerLogin("google", "ana", "ana@example.test", true, "Ana");
    }

    @Test
    @DisplayName("conta comum recebe 403, inclusive pelo caminho percent-encoded")
    void aCommonAccountIsRefused() throws Exception {
        mockMvc.perform(get("/api/admin/painel").with(as("ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));

        // URI, e não String: o builder de String reencoda o `%` e o teste passaria por engano.
        mockMvc.perform(get(URI.create("/api/%61dmin/painel")).with(as("ana")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("nao_autorizado"));

        mockMvc.perform(get("/api/admin/painel")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("nenhum campo da resposta identifica uma pessoa")
    void theResponseNamesNobody() throws Exception {
        plantar(HOJE.minusDays(1), UsageDimension.EVENTO, UsageEventType.PAGINA.name(), 30, 9);

        String corpo =
                mockMvc.perform(get("/api/admin/painel").with(as("dono")))
                        .andExpect(status().isOk())
                        .andExpect(content().contentTypeCompatibleWith("application/json"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
                        .toLowerCase(Locale.ROOT);

        // O corpo inteiro, e não campo a campo: o que se quer barrar é o campo que alguém
        // acrescentar depois sem lembrar do que esta tela não pode mostrar.
        // A arroba cobre qualquer endereço, inclusive um que não seja o das contas plantadas aqui.
        assertThat(corpo)
                .doesNotContain("@")
                .doesNotContain("userid")
                .doesNotContain("user_id")
                .doesNotContain("visitkey")
                .doesNotContain("visit_key");
    }

    @Test
    @DisplayName("os seis degraus do funil vêm sempre, e degrau zerado aparece como zero")
    void theFunnelAlwaysHasSixSteps() throws Exception {
        LocalDate ontem = HOJE.minusDays(1);
        plantar(ontem, UsageDimension.EVENTO, UsageEventType.PAGINA.name(), 40, 10);
        plantar(ontem, UsageDimension.EVENTO, UsageEventType.CADASTRO_CRIADO.name(), 2, 2);

        mockMvc.perform(get("/api/admin/painel").with(as("dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funnel.length()").value(6))
                .andExpect(jsonPath("$.funnel[0].step").value("VISITA"))
                // Visitantes, e não acessos: o primeiro degrau conta gente.
                .andExpect(jsonPath("$.funnel[0].total").value(10))
                .andExpect(jsonPath("$.funnel[0].percentOfPrevious").doesNotExist())
                .andExpect(jsonPath("$.funnel[1].step").value("DEMONSTRACAO_ABERTA"))
                .andExpect(jsonPath("$.funnel[1].total").value(0))
                .andExpect(jsonPath("$.funnel[2].step").value("CADASTRO_CRIADO"))
                .andExpect(jsonPath("$.funnel[2].total").value(2))
                // Degrau anterior zerado não vira 0%: não há conversão a afirmar.
                .andExpect(jsonPath("$.funnel[2].percentOfPrevious").doesNotExist())
                .andExpect(jsonPath("$.funnel[5].step").value("RETORNO_EM_7_DIAS"))
                .andExpect(jsonPath("$.funnel[5].total").value(0));
    }

    @Test
    @DisplayName("os três presets são aceitos e qualquer outro período é 400")
    void onlyThePresetsAreAccepted() throws Exception {
        for (int dias : new int[] {7, 30, 90}) {
            mockMvc.perform(
                            get("/api/admin/painel")
                                    .param("dias", String.valueOf(dias))
                                    .with(as("dono")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.days").value(dias))
                    .andExpect(jsonPath("$.access.series.length()").value(dias));
        }

        mockMvc.perform(get("/api/admin/painel").param("dias", "14").with(as("dono")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    @DisplayName("sem agregação nenhuma, o painel abre zerado e diz que nada foi fechado ainda")
    void withoutAnyAggregationThePanelStillOpens() throws Exception {
        mockMvc.perform(get("/api/admin/painel").with(as("dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregatedThrough").doesNotExist())
                .andExpect(jsonPath("$.access.visits").value(0))
                .andExpect(jsonPath("$.access.visitors").value(0))
                // Sete pontos, todos zero: dia sem acesso é ponto na série, não buraco nela.
                .andExpect(jsonPath("$.access.series.length()").value(7))
                .andExpect(jsonPath("$.origins.length()").value(0))
                .andExpect(jsonPath("$.funnel.length()").value(6))
                // As contas existem mesmo sem coleta nenhuma — são leitura de outra tabela.
                .andExpect(jsonPath("$.accounts.total").value(greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("o período termina ontem e o comparativo é o período anterior de mesmo tamanho")
    void theWindowEndsYesterdayAndComparesWithThePreviousOne() throws Exception {
        plantar(HOJE, UsageDimension.EVENTO, UsageEventType.PAGINA.name(), 999, 999);
        plantar(HOJE.minusDays(1), UsageDimension.EVENTO, UsageEventType.PAGINA.name(), 10, 4);
        plantar(HOJE.minusDays(8), UsageDimension.EVENTO, UsageEventType.PAGINA.name(), 6, 3);

        mockMvc.perform(get("/api/admin/painel").with(as("dono")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-20"))
                .andExpect(jsonPath("$.to").value("2026-08-26"))
                .andExpect(jsonPath("$.previousFrom").value("2026-08-13"))
                .andExpect(jsonPath("$.previousTo").value("2026-08-19"))
                // Hoje ficou de fora: ele ainda recebe evento, e publicá-lo daria um número que
                // muda depois de lido.
                .andExpect(jsonPath("$.access.visits").value(10))
                .andExpect(jsonPath("$.access.visitors").value(4))
                .andExpect(jsonPath("$.access.previousVisits").value(6))
                .andExpect(jsonPath("$.access.previousVisitors").value(3));
    }

    private void plantar(
            LocalDate dia, UsageDimension dimensao, String valor, long eventos, long visitas) {
        daily.save(new UsageDaily(dia, dimensao, valor, eventos, visitas));
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
                .attributes(attributes -> attributes.put("sub", subject));
    }
}
