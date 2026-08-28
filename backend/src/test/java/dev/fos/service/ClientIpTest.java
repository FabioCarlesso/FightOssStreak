package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.config.FosProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * De onde sai o endereço que chaveia os freios (#77).
 *
 * <p>Os dois casos que importam estão aqui lado a lado: o que o <b>cliente</b> escreve nunca decide
 * a chave, e o que a <b>borda</b> escreve decide — em qual posição da cadeia, é configuração.
 */
class ClientIpTest {

    /**
     * A cadeia como ela chega em produção, copiada do log de acesso do nginx na Railway: a borda
     * descarta o {@code X-Forwarded-For} de quem chama e entrega {@code <visitante>, <nó de
     * borda>}; o nginx anexa o próprio peer.
     */
    private static final String CADEIA_RAILWAY = "104.28.228.100, 152.233.23.193, 100.64.0.11";

    @Test
    @DisplayName("sem o header da borda vale o peer da conexão")
    void withoutTheEdgeHeaderThePeerWins() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");

        assertThat(clientIp(1).of(request)).isEqualTo("192.0.2.10");
    }

    @Test
    @DisplayName("um salto confiável: o último da cadeia, que é o peer que o nginx enxergou")
    void oneTrustedHopReadsTheLastElement() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // O que o cliente escreveu vai para a cabeça da lista quando o nginx acrescenta o peer.
        request.addHeader(ClientIp.HEADER, "1.2.3.4, 198.51.100.7");

        assertThat(clientIp(1).of(request)).isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("três saltos: o visitante atrás da borda da Railway, como medido em produção")
    void threeTrustedHopsReadTheVisitorBehindTheEdge() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIp.HEADER, CADEIA_RAILWAY);

        assertThat(clientIp(3).of(request)).isEqualTo("104.28.228.100");
    }

    @Test
    @DisplayName("o número errado colapsa todo mundo no nó de borda — ruído visível, não bypass")
    void aWrongHopCountCollapsesEveryoneOnAnInfrastructureAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIp.HEADER, CADEIA_RAILWAY);

        // Com 2 a chave vira o nó de borda da plataforma, que é o mesmo para todo mundo que
        // entra por ele: os freios passam a recusar gente legítima. É por isso que a variável
        // entra no mesmo deploy que o código.
        assertThat(clientIp(2).of(request)).isEqualTo("152.233.23.193");
    }

    @Test
    @DisplayName("cadeia mais curta que o configurado cai no último elemento, não estoura")
    void aShorterChainFallsBackToTheLastElement() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIp.HEADER, "10.250.0.3");

        // Topologia diferente da declarada: todo mundo divide a chave, que é o extremo seguro.
        assertThat(clientIp(3).of(request)).isEqualTo("10.250.0.3");
    }

    @Test
    @DisplayName("saltos a mais não devolvem a chave para quem chama: vale o salto mais próximo")
    void aChainShorterThanConfiguredNeverYieldsTheClientWrittenHead() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // A cadeia do Compose (só o nginx na frente) chegando a um backend configurado como se
        // houvesse borda: o que o cliente escreveu está na cabeça, o peer que o nginx viu no fim.
        request.addHeader(ClientIp.HEADER, "203.0.113.9, 172.18.0.9");

        // Cair na cabeça aqui seria o defeito da #77 de volta — número errado para cima abrindo
        // a porta em silêncio, em vez de colapsar a chave como errar para baixo faz.
        assertThat(clientIp(3).of(request)).isEqualTo("172.18.0.9");
    }

    @Test
    @DisplayName("header vazio vale o mesmo que header ausente")
    void anEmptyHeaderIsNoHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader(ClientIp.HEADER, "   ");

        assertThat(clientIp(1).of(request)).isEqualTo("192.0.2.10");
    }

    @Test
    @DisplayName("o peer vem de baixo dos wrappers, não do que o ForwardedHeaderFilter reescreveu")
    void thePeerComesFromUnderTheWrappers() {
        MockHttpServletRequest original = new MockHttpServletRequest();
        original.setRemoteAddr("192.0.2.10");
        // É isto que o ForwardedHeaderFilter faz com o X-Forwarded-For do cliente: devolve o
        // primeiro elemento dele em getRemoteAddr(). Sem descer até o request de baixo, seria
        // este o valor da chave — e ele é escolhido por quem chama.
        HttpServletRequest disfarcado =
                new HttpServletRequestWrapper(original) {
                    @Override
                    public String getRemoteAddr() {
                        return "1.2.3.4";
                    }
                };

        assertThat(clientIp(1).of(disfarcado)).isEqualTo("192.0.2.10");
    }

    @Test
    @DisplayName("requisição nula vira string vazia, não NPE")
    void aNullRequestIsEmpty() {
        assertThat(clientIp(1).of(null)).isEmpty();
    }

    private static ClientIp clientIp(int saltos) {
        return new ClientIp(
                new FosProperties(
                        "test", null, null, null, null, null, new FosProperties.Proxy(saltos)));
    }
}
