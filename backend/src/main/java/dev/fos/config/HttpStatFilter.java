package dev.fos.config;

import dev.fos.model.HttpStatHourly;
import dev.fos.service.CookieSecureTopology;
import dev.fos.service.HttpStatCollector;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Onde uma requisição vira medição (#86).
 *
 * <p><b>Filtro, e não interceptor do MVC</b>, e a diferença é o que se consegue ver: interceptor só
 * roda no que chega ao {@code DispatcherServlet}, e as respostas mais interessantes para o alerta —
 * o 401 da cadeia de segurança, o 401 do {@code ConcurrentSessionFilter} de quem foi desconectado —
 * são respondidas <em>antes</em> disso. Um monitor de saúde que não enxerga o 401 do filtro seria
 * cego justamente no pico de 401 que ele existe para achar.
 *
 * <p><b>A rota gravada é o padrão que o roteamento casou</b> ({@code
 * /api/admin/usuarios/{id}/status}), lido do atributo que o MVC deixa na requisição depois do
 * dispatch. É a mesma guarda de privacidade que o {@code UsagePaths} faz na coleta de uso, por
 * outro caminho: padrão não tem segmento variável, então nem token de confirmação nem id de conta
 * têm por onde entrar na tabela. O que não casou padrão nenhum — 404, e as respostas do filtro que
 * nunca chegaram ao MVC — vira {@link HttpStatHourly#SEM_ROTA}, uma categoria só.
 *
 * <p>Fora da medição: o {@code /actuator/health}. Ele é consultado pela plataforma e pelo Compose a
 * cada poucos segundos, e seria de longe a rota mais movimentada do painel — afogando o tráfego de
 * verdade e inflando a disponibilidade com requisições que o próprio monitoramento gera.
 *
 * <p>Ordem: logo depois do {@code ForwardedHeaderFilter} e antes da cadeia de segurança, para que o
 * tempo medido seja o tempo que quem chamou esperou, e o status seja o que ele recebeu.
 *
 * <p>É também daqui que o {@link CookieSecureTopology} olha o esquema da requisição (#74), e é por
 * causa dessa ordem: <b>depois</b> do {@code ForwardedHeaderFilter} o {@code isSecure()} já reflete
 * o {@code X-Forwarded-Proto} da borda, e antes dele diria {@code http} em toda requisição. Um
 * filtro próprio para a conferência precisaria repetir essa sutileza de ordenação para não mentir;
 * este já está no lugar certo, e o custo é ler um booleano por requisição.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class HttpStatFilter extends OncePerRequestFilter {

    private final HttpStatCollector collector;
    private final CookieSecureTopology cookie;

    HttpStatFilter(HttpStatCollector collector, CookieSecureTopology cookie) {
        this.collector = collector;
        this.cookie = cookie;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI();
        return caminho != null && caminho.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        cookie.observe(request.isSecure());
        long inicio = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            // No `finally`, e não depois do `doFilter`: a requisição que estourou exceção é a que
            // mais importa medir, e ela nunca chegaria na linha seguinte.
            collector.record(
                    rota(request), response.getStatus(), (System.nanoTime() - inicio) / 1_000_000);
        }
    }

    /** O padrão casado, quando houve um. Nunca o caminho cru. */
    private static String rota(HttpServletRequest request) {
        Object padrao = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (padrao == null) {
            return HttpStatHourly.SEM_ROTA;
        }
        String texto = padrao.toString();
        return texto.isBlank() ? HttpStatHourly.SEM_ROTA : texto;
    }
}
