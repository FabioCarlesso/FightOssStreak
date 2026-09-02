package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.fos.config.FosProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * O deploy em que o código e a variável não vieram juntos precisa se anunciar (#97).
 *
 * <p>É o defeito que a #96 teve em produção: {@code FOS_PROXY_TRUSTED_HOPS} não entrou no mesmo
 * deploy que o código, o backend rodou com o default do Compose e nada apareceu no log — o valor
 * era válido, só não era o certo. Estes testes são o que reprova a volta do silêncio.
 */
class ProxyTopologyTest {

    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> avisos;

    @BeforeEach
    void capturarOLog() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProxyTopology.class);
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
    @DisplayName("cadeia mais curta que o declarado avisa, nomeando a variável e os dois números")
    void aShorterChainWarnsNamingTheVariableAndBothNumbers() {
        // A topologia do Compose (nginx e mais nada) chegando a um backend declarado como Railway.
        topology(3).observe(2);

        assertThat(mensagens())
                .singleElement()
                .asString()
                .contains("FOS_PROXY_TRUSTED_HOPS")
                .contains("3")
                .contains("2");
    }

    @Test
    @DisplayName("cadeia mais longa que o declarado também avisa: pode ser proxy novo na frente")
    void aLongerChainWarnsToo() {
        // O caso da #96 ao contrário — e o caso de pôr uma CDN na frente do domínio.
        topology(1).observe(3);

        assertThat(mensagens())
                .singleElement()
                .asString()
                .contains("FOS_PROXY_TRUSTED_HOPS")
                .contains("1")
                .contains("3");
    }

    @Test
    @DisplayName("nenhum dos dois lados manda copiar o número: os dois pedem conferência")
    void neitherSideTellsTheReaderToCopyTheObservedNumber() {
        // Onde nenhuma borda saneia o X-Forwarded-For, quem chama consegue alongar a cadeia — e
        // com trusted-hops declarado acima da topologia real, um elemento forjado ainda cabe
        // embaixo do declarado. Um aviso mandando copiar o número seria mandar entregar a chave.
        topology(1).observe(4);
        assertThat(mensagens()).singleElement().asString().contains("Confira a topologia");

        avisos.list.clear();
        topology(3).observe(2);
        assertThat(mensagens()).singleElement().asString().contains("Confira a topologia");
    }

    @Test
    @DisplayName("é o ClientIp quem conta: ler uma cadeia divergente avisa")
    void theWarningComesFromReadingARealRequest() {
        // Sem este teste, apagar a chamada a observe() do ClientIp deixaria o resto verde e o
        // aviso sumiria do app inteiro — que é exatamente o silêncio que a #97 veio remover.
        ProxyTopology topology = topology(3);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIp.HEADER, "203.0.113.9, 172.18.0.9");

        String chave = new ClientIp(topology).of(request);

        assertThat(chave).isEqualTo("172.18.0.9");
        assertThat(mensagens()).singleElement().asString().contains("FOS_PROXY_TRUSTED_HOPS");
    }

    @Test
    @DisplayName("declarado e observado batendo não escrevem nada: deploy correto tem log limpo")
    void aMatchingTopologyStaysSilent() {
        ProxyTopology topology = topology(3);

        topology.observe(3);
        topology.observe(3);

        assertThat(mensagens()).isEmpty();
    }

    @Test
    @DisplayName("a mesma divergência não repete a cada requisição")
    void theSameMismatchDoesNotRepeatOnEveryRequest() {
        ProxyTopology topology = topology(3);

        for (int i = 0; i < 50; i++) {
            topology.observe(2);
        }

        // Um aviso por janela: repetir viraria ruído que ninguém lê.
        assertThat(mensagens()).hasSize(1);
    }

    @Test
    @DisplayName("passada a janela, a divergência que continua volta ao log")
    void aMismatchThatPersistsComesBackAfterTheWindow() {
        Instant inicio = Instant.parse("2026-01-10T12:00:00Z");
        RelogioDeTeste relogio = new RelogioDeTeste(inicio);
        ProxyTopology topology = topology(3, relogio);

        topology.observe(2);
        relogio.avancar(ProxyTopology.JANELA.plusMinutes(1));
        topology.observe(2);

        // Avisar uma vez só sumiria do log de quem for olhar horas depois.
        assertThat(mensagens()).hasSize(2);
    }

    @Test
    @DisplayName("variar o tamanho da cadeia não abre o log: a janela vale para qualquer número")
    void varyingTheChainLengthDoesNotFloodTheLog() {
        ProxyTopology topology = topology(3);

        // O tamanho da cadeia é em parte escrito por quem chama; se cada número novo valesse um
        // aviso, bastaria variá-lo para encher o log.
        for (int elementos = 4; elementos < 40; elementos++) {
            topology.observe(elementos);
        }

        assertThat(mensagens()).hasSize(1);
    }

    @Test
    @DisplayName("cadeia vazia não avisa: requisição sem nginx na frente é dev, não divergência")
    void anEmptyChainIsNotAMismatch() {
        topology(3).observe(0);

        assertThat(mensagens()).isEmpty();
    }

    @Test
    @DisplayName("nenhum endereço entra no texto do aviso — contar elementos não é registrar IP")
    void theWarningNeverCarriesAnAddress() {
        topology(3).observe(2);

        // A promessa de docs/11-privacidade.md vale para o log também.
        assertThat(mensagens()).singleElement().asString().doesNotContainPattern("\\d+\\.\\d+\\.");
    }

    private List<String> mensagens() {
        return avisos.list.stream()
                .filter(evento -> evento.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static ProxyTopology topology(int saltos) {
        return topology(saltos, Clock.systemUTC());
    }

    private static ProxyTopology topology(int saltos, Clock clock) {
        return new ProxyTopology(
                new FosProperties(
                        "test",
                        null,
                        null,
                        null,
                        null,
                        null,
                        new FosProperties.Proxy(saltos),
                        null,
                        null),
                clock);
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
        public Instant instant() {
            return agora;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
