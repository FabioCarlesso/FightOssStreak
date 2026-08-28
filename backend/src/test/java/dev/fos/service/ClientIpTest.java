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

    private static final String CADEIA_RAILWAY = "1.2.3.4, 198.51.100.7, 10.250.0.3";

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
    @DisplayName("dois saltos confiáveis: o penúltimo, que é o visitante atrás da borda")
    void twoTrustedHopsReadTheVisitorBehindTheEdge() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIp.HEADER, CADEIA_RAILWAY);

        assertThat(clientIp(2).of(request)).isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("cadeia mais curta que o configurado cai no primeiro elemento, não estoura")
    void aShorterChainFallsBackToTheFirstElement() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ClientIp.HEADER, "10.250.0.3");

        // Topologia diferente da declarada: todo mundo divide a chave, que é o extremo seguro.
        assertThat(clientIp(3).of(request)).isEqualTo("10.250.0.3");
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
