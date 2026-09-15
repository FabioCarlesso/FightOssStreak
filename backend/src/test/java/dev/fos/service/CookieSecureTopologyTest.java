package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.ServerProperties;

/**
 * O deploy em que o código da #74 subiu e {@code FOS_COOKIE_SECURE} não precisa se anunciar.
 *
 * <p>É o defeito da #96 com outra variável: {@code false} é valor válido, então sem este aviso o
 * app roda sem exceção e sem log, com o cookie de sessão saindo em produção sem o flag — que é o
 * estado que a #74 veio consertar. Estes testes são o que reprova a volta do silêncio.
 */
class CookieSecureTopologyTest {

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
    @DisplayName("https na borda com o flag desligado avisa, nomeando a variável")
    void httpsWithTheFlagOffWarnsNamingTheVariable() {
        // O deploy da #74 sem a variável: é este o estado que ninguém enxerga sem o aviso.
        topology(false).observe(true);

        assertThat(mensagens()).singleElement().asString().contains("FOS_COOKIE_SECURE");
    }

    @Test
    @DisplayName("o aviso não manda ligar de olhos fechados: pede confirmar que o TLS é seu")
    void theWarningDoesNotTellTheReaderToFlipItBlindly() {
        // O X-Forwarded-Proto atravessa o nginx vindo de quem chama quando ninguém o saneia, e
        // ligar Secure sem TLS de verdade derruba a sessão de todo host que não seja localhost —
        // medido no navegador. Um aviso imperativo transformaria um header forjado em incidente.
        topology(false).observe(true);

        assertThat(mensagens())
                .singleElement()
                .asString()
                .contains("Confirme")
                .contains("localhost");
    }

    @Test
    @DisplayName("http com o flag ligado avisa também — e é o lado em que o app não funciona")
    void plainHttpWithTheFlagOnWarnsToo() {
        // O contrário do defeito da #74, e com sintoma pior: o navegador descarta o cookie em todo
        // host que não seja localhost, então não há sessão. Sem este aviso, o log fica mudo
        // justamente quando o app parou de funcionar.
        topology(true).observe(false);

        assertThat(mensagens())
                .singleElement()
                .asString()
                .contains("FOS_COOKIE_SECURE")
                .contains("401");
    }

    @Test
    @DisplayName("o aviso do lado http também não manda mexer de olhos fechados")
    void theHttpSideWarningDoesNotTellTheReaderToFlipItBlindly() {
        // O esquema observado é afirmação de quem está na frente, não fato: desligar o flag por
        // causa de uma requisição que não passou pela borda seria desfazer o conserto da #74.
        topology(true).observe(false);

        assertThat(mensagens())
                .singleElement()
                .asString()
                .contains("Se este ambiente não tem TLS")
                .contains("X-Forwarded-Proto");
    }

    @Test
    @DisplayName("os dois lados dizem o que fazer com a variável, e dizem coisas diferentes")
    void eachSideNamesTheValueThatFixesIt() {
        topology(false).observe(true);
        assertThat(mensagens()).singleElement().asString().contains("FOS_COOKIE_SECURE=true");

        avisos.list.clear();
        topology(true).observe(false);
        assertThat(mensagens()).singleElement().asString().contains("FOS_COOKIE_SECURE=false");
    }

    @Test
    @DisplayName("http com o flag ligado não repete a cada requisição")
    void theHttpSideMismatchDoesNotRepeatOnEveryRequest() {
        CookieSecureTopology topology = topology(true);

        for (int i = 0; i < 50; i++) {
            topology.observe(false);
        }

        assertThat(mensagens()).hasSize(1);
    }

    @Test
    @DisplayName("produção configurada não escreve nada: deploy correto tem log limpo")
    void aMatchingProductionStaysSilent() {
        CookieSecureTopology topology = topology(true);

        topology.observe(true);
        topology.observe(true);

        assertThat(mensagens()).isEmpty();
    }

    @Test
    @DisplayName("http com o flag desligado é dev e o Compose, não divergência")
    void plainHttpWithTheFlagOffIsNotAMismatch() {
        // O caso normal de `npm run dev:backend` e de `docker compose up`. Avisar aqui encheria o
        // log de todo mundo que roda o projeto na própria máquina.
        CookieSecureTopology topology = topology(false);

        for (int i = 0; i < 50; i++) {
            topology.observe(false);
        }

        assertThat(mensagens()).isEmpty();
    }

    @Test
    @DisplayName("a mesma divergência não repete a cada requisição")
    void theSameMismatchDoesNotRepeatOnEveryRequest() {
        CookieSecureTopology topology = topology(false);

        for (int i = 0; i < 50; i++) {
            topology.observe(true);
        }

        assertThat(mensagens()).hasSize(1);
    }

    @Test
    @DisplayName("passada a janela, a divergência que continua volta ao log")
    void aMismatchThatPersistsComesBackAfterTheWindow() {
        RelogioDeTeste relogio = new RelogioDeTeste(Instant.parse("2026-01-10T12:00:00Z"));
        CookieSecureTopology topology = topology(false, relogio);

        topology.observe(true);
        relogio.avancar(CookieSecureTopology.JANELA.plusMinutes(1));
        topology.observe(true);

        assertThat(mensagens()).hasSize(2);
    }

    @Test
    @DisplayName("flag ausente é 'não marque', e é divergência igual")
    void anAbsentFlagIsTreatedAsOff() {
        // ServerProperties ignora chave desconhecida: um erro de digitação sob `cookie:` deixaria
        // o flag em null, que o Tomcat lê como "não marque". Silenciar o null devolveria o
        // silêncio justamente ao caso mais difícil de notar.
        topology(null).observe(true);

        assertThat(mensagens()).singleElement().asString().contains("FOS_COOKIE_SECURE");
    }

    @Test
    @DisplayName("o aviso não carrega endereço, rota nem identificador de sessão")
    void theWarningCarriesNothingThatIdentifiesAnyone() {
        // A promessa de docs/11-privacidade.md vale para o log também (D50).
        topology(false).observe(true);

        assertThat(mensagens())
                .singleElement()
                .asString()
                .doesNotContainPattern("\\d+\\.\\d+\\.")
                .doesNotContain("JSESSIONID");
    }

    private List<String> mensagens() {
        return avisos.list.stream()
                .filter(evento -> evento.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static CookieSecureTopology topology(Boolean secure) {
        return topology(secure, Clock.systemUTC());
    }

    private static CookieSecureTopology topology(Boolean secure, Clock clock) {
        ServerProperties properties = new ServerProperties();
        properties.getServlet().getSession().getCookie().setSecure(secure);
        return new CookieSecureTopology(properties, clock);
    }

    /** Relógio que anda quando o teste manda — a janela do aviso é medida em horas. */
    private static final class RelogioDeTeste extends Clock {

        private Instant agora;

        private RelogioDeTeste(Instant inicio) {
            this.agora = inicio;
        }

        private void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }
}
