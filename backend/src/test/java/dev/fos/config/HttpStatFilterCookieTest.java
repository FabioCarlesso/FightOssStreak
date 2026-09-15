package dev.fos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.fos.service.CookieSecureTopology;
import dev.fos.service.HttpStatCollector;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Quem liga o {@link CookieSecureTopology} à requisição é o {@link HttpStatFilter} — e é isto que
 * este teste prende.
 *
 * <p>Sem ele, apagar a chamada a {@code observe()} do filtro deixaria o {@code
 * CookieSecureTopologyTest} inteiro verde e o aviso sumiria do app, que é exatamente o silêncio que
 * a #74 veio remover. É o mesmo par que o {@code ProxyTopologyTest} mantém com o {@code ClientIp}.
 *
 * <p>Prende também a <b>ordem</b>: o filtro olha o {@code isSecure()} da requisição já embrulhada
 * pelo {@code ForwardedHeaderFilter}. Um {@code MockHttpServletRequest} com esquema {@code https} é
 * o que aquele embrulho produz quando a borda encaminha {@code X-Forwarded-Proto: https}.
 */
class HttpStatFilterCookieTest {

    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> avisos;

    @BeforeEach
    void capturarOLog() {
        logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(CookieSecureTopology.class);
        avisos = new ListAppender<>();
        avisos.start();
        logger.addAppender(avisos);
    }

    @AfterEach
    void soltarOLog() {
        logger.detachAppender(avisos);
        avisos.stop();
    }

    @Test
    @DisplayName("requisição https atravessando o filtro avisa que o cookie está sem Secure")
    void anHttpsRequestThroughTheFilterWarns() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");
        request.setSecure(true);
        request.setScheme("https");

        filtro(false).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(mensagens()).singleElement().asString().contains("FOS_COOKIE_SECURE");
    }

    @Test
    @DisplayName("a mesma requisição em http não avisa: é dev e o Compose atravessando o filtro")
    void thePlainHttpRequestStaysSilent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me");

        filtro(false).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(mensagens()).isEmpty();
    }

    private List<String> mensagens() {
        return avisos.list.stream()
                .filter(evento -> evento.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static HttpStatFilter filtro(boolean secure) {
        ServerProperties properties = new ServerProperties();
        properties.getServlet().getSession().getCookie().setSecure(secure);
        return new HttpStatFilter(
                mock(HttpStatCollector.class),
                new CookieSecureTopology(properties, Clock.systemUTC()));
    }
}
