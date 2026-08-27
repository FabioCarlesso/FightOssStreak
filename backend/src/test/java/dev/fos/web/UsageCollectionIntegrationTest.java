package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.fos.model.AppUser;
import dev.fos.model.DeviceClass;
import dev.fos.model.UsageEvent;
import dev.fos.model.UsageEventType;
import dev.fos.repo.UsageEventRepository;
import dev.fos.service.AccountService;
import dev.fos.service.UsageCollector;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * A coleta de uso, ponta a ponta (#84, D50).
 *
 * <p>O que estes testes protegem são promessas escritas em {@code docs/11-privacidade.md}, e não
 * comportamento de conveniência: o servidor deriva o que dá para derivar e ignora o corpo forjado,
 * nenhum cookie novo é criado, nenhum token de rota entra na tabela, e {@code DELETE /api/me} leva
 * os eventos crus da conta junto.
 *
 * <p>Um Chrome de Android como {@code User-Agent} padrão para que dispositivo, navegador e sistema
 * tenham o que derivar — e nenhuma base de geolocalização, que é como dev e CI rodam.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(UsageCollectionIntegrationTest.FixedClockConfig.class)
@Transactional
class UsageCollectionIntegrationTest {

    private static final String CHROME_ANDROID =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/126.0.0.0 Mobile Safari/537.36";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UsageEventRepository events;
    @Autowired private AccountService accounts;

    @BeforeEach
    void limpar() {
        events.deleteAll();
    }

    @Test
    @DisplayName("quem não tem sessão registra acesso — é justamente quem a issue quer contar")
    void anonymousVisitIsCollected() throws Exception {
        mockMvc.perform(evento("{\"caminho\":\"/\",\"utmSource\":\"whatsapp\"}"))
                .andExpect(status().isNoContent());

        UsageEvent evento = unico();
        assertThat(evento.getEventType()).isEqualTo(UsageEventType.PAGINA);
        assertThat(evento.getPath()).isEqualTo("/");
        assertThat(evento.getUtmSource()).isEqualTo("whatsapp");
        assertThat(evento.getUserId()).isNull();
        assertThat(evento.getVisitKey()).hasSize(64);
        assertThat(evento.origin()).isEqualTo("whatsapp");
    }

    @Test
    @DisplayName("o servidor deriva dispositivo, navegador, sistema e idioma da própria requisição")
    void serverDerivesFromTheRequest() throws Exception {
        mockMvc.perform(evento("{\"caminho\":\"/arvore\"}")).andExpect(status().isNoContent());

        UsageEvent evento = unico();
        assertThat(evento.getDevice()).isEqualTo(DeviceClass.CELULAR);
        assertThat(evento.getBrowser()).isEqualTo("chrome");
        assertThat(evento.getOs()).isEqualTo("android");
        assertThat(evento.getLanguage()).isEqualTo("pt");
    }

    @Test
    @DisplayName(
            "corpo forjado não muda o que o servidor deriva — nem dispositivo, nem país, nem tipo")
    void forgedBodyChangesNothing() throws Exception {
        mockMvc.perform(
                        evento(
                                """
                                {"caminho":"/hoje","device":"DESKTOP","country":"US",
                                 "eventType":"CADASTRO_CRIADO","userId":42,
                                 "visitKey":"chave-escolhida-por-mim"}
                                """))
                .andExpect(status().isNoContent());

        UsageEvent evento = unico();
        assertThat(evento.getDevice()).isEqualTo(DeviceClass.CELULAR);
        assertThat(evento.getCountry()).isEqualTo(UsageEvent.PAIS_DESCONHECIDO);
        assertThat(evento.getEventType()).isEqualTo(UsageEventType.PAGINA);
        assertThat(evento.getUserId()).isNull();
        assertThat(evento.getVisitKey()).isNotEqualTo("chave-escolhida-por-mim");
    }

    @Test
    @DisplayName(
            "nenhum token de rota entra na tabela: o caminho é normalizado antes de virar linha")
    void routeTokensNeverLand() throws Exception {
        mockMvc.perform(evento("{\"caminho\":\"/confirmar-email/token-secreto-de-verdade\"}"))
                .andExpect(status().isNoContent());

        assertThat(unico().getPath()).isEqualTo("/confirmar-email/{token}");
    }

    @Test
    @DisplayName("a coleta não cria cookie de rastreio — é o que dispensa banner de consentimento")
    void noTrackingCookieIsCreated() throws Exception {
        MvcResult resultado =
                mockMvc.perform(evento("{\"caminho\":\"/\"}"))
                        .andExpect(status().isNoContent())
                        .andReturn();

        // O que precisa ser verdade: a coleta não acrescentou cookie nenhum. O único nome que
        // pode aparecer é o token de CSRF, que a cadeia de segurança emite em qualquer resposta
        // desde muito antes desta issue, não identifica ninguém entre requisições e some com a
        // aba. Nada de cookie de visitante, e nada de sessão para quem não tem sessão — abrir uma
        // sessão aqui seria criar identificador estável pela porta dos fundos.
        assertThat(resultado.getResponse().getCookies())
                .extracting(jakarta.servlet.http.Cookie::getName)
                .isSubsetOf("XSRF-TOKEN");
        assertThat(resultado.getResponse().getCookie("JSESSIONID")).isNull();
    }

    @Test
    @DisplayName("país desconhecido sem base de geolocalização — e a aplicação subiu igual")
    void unknownCountryWithoutDatabase() throws Exception {
        mockMvc.perform(evento("{\"caminho\":\"/\"}")).andExpect(status().isNoContent());

        assertThat(unico().getCountry()).isEqualTo(UsageEvent.PAIS_DESCONHECIDO);
        assertThat(unico().getRegion()).isEqualTo(UsageEvent.DESCONHECIDO);
    }

    @Test
    @DisplayName("corpo sem nada, ou com lixo, não quebra — evento perdido nunca é tela quebrada")
    void garbageNeverBreaks() throws Exception {
        mockMvc.perform(evento("{}")).andExpect(status().isNoContent());
        mockMvc.perform(evento("{\"caminho\":\"/\",\"utmSource\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isNoContent());

        List<UsageEvent> registrados = events.findAll();
        assertThat(registrados).hasSize(2);
        assertThat(registrados.get(0).getPath()).isEqualTo("/outro");
        // Alfabeto restrito: a dimensão ORIGEM do painel não é campo de texto livre de quem quiser.
        assertThat(registrados.get(1).getUtmSource()).isEqualTo(UsageEvent.DESCONHECIDO);
    }

    @Test
    @DisplayName("com sessão, o evento sai com a conta; DELETE /api/me leva os eventos crus junto")
    void accountDeletionTakesTheRawEvents() throws Exception {
        AppUser conta =
                accounts.registerLogin("google", "aluno", "aluno@example.test", true, "Aluno");

        mockMvc.perform(evento("{\"caminho\":\"/hoje\"}").with(as("google", "aluno")))
                .andExpect(status().isNoContent());

        assertThat(unico().getUserId()).isEqualTo(conta.getId());

        mockMvc.perform(delete("/api/me").with(as("google", "aluno")).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(events.countByUserId(conta.getId())).isZero();
    }

    @Test
    @DisplayName("o primeiro drill vira evento de funil — e só o primeiro")
    void firstDrillIsAFunnelEvent() throws Exception {
        AppUser conta =
                accounts.registerLogin("google", "aluno", "aluno@example.test", true, "Aluno");

        mockMvc.perform(drill("M0.1")).andExpect(status().isOk());

        assertThat(tipos()).containsExactly(UsageEventType.PRIMEIRO_DRILL);
        UsageEvent funil = unico();
        assertThat(funil.getPath()).isEqualTo(UsageCollector.SEM_CAMINHO);
        assertThat(funil.getUserId()).isEqualTo(conta.getId());

        // O segundo drill não emite nada: o evento é "primeiro drill", não "drill".
        mockMvc.perform(drill("M0.2")).andExpect(status().isOk());

        assertThat(tipos()).containsExactly(UsageEventType.PRIMEIRO_DRILL);
    }

    private static MockHttpServletRequestBuilder drill(String codigo) {
        return post("/api/nodes/" + codigo + "/drill")
                .with(as("google", "aluno"))
                .with(csrf())
                .header("User-Agent", CHROME_ANDROID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recall\":\"OK\"}");
    }

    /** Sessão do par (provedor, subject) — o mesmo que o fluxo real deixa na autenticação. */
    private static RequestPostProcessor as(String provider, String subject) {
        return oauth2Login()
                .clientRegistration(
                        ClientRegistration.withRegistrationId(provider)
                                .clientId("test")
                                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                                .redirectUri("{baseUrl}/api/login/oauth2/code/" + provider)
                                .authorizationUri("https://example.test/authorize")
                                .tokenUri("https://example.test/token")
                                .userInfoUri("https://example.test/userinfo")
                                .userNameAttributeName("sub")
                                .build())
                .attributes(attributes -> attributes.put("sub", subject));
    }

    private List<UsageEventType> tipos() {
        return events.findAll().stream().map(UsageEvent::getEventType).toList();
    }

    private UsageEvent unico() {
        List<UsageEvent> registrados = events.findAll();
        assertThat(registrados).hasSize(1);
        return registrados.get(0);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            evento(String corpo) {
        return post("/api/telemetria/evento")
                .contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", CHROME_ANDROID)
                .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                .content(corpo);
    }
}
