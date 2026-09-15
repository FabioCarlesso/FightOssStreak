package dev.fos.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.stereotype.Component;

/**
 * O flag {@code Secure} <b>declarado</b> ({@code server.servlet.session.cookie.secure}, via {@code
 * FOS_COOKIE_SECURE}) confrontado com o esquema <b>observado</b> na requisição (#74).
 *
 * <p><b>Por que existe.</b> É o mesmo par da D51/#96 que o {@link ProxyTopology} guarda, com outra
 * variável: o código entra por PR e a variável por clique no painel da plataforma, e {@code false}
 * é valor válido — um deploy em que só o código subiu roda sem exceção, sem log e com o cookie de
 * sessão viajando sem o flag, que é exatamente o defeito que a #74 veio consertar. Sem este aviso,
 * o jeito de descobrir seria repetir o {@code curl} do README de tempos em tempos e lembrar de
 * fazê-lo.
 *
 * <p><b>O que ele observa.</b> {@code isSecure()} lido depois do {@code ForwardedHeaderFilter}, ou
 * seja, o {@code X-Forwarded-Proto} que a borda encaminhou. Configuração batendo com o tráfego —
 * {@code https} com o flag ligado, {@code http} com ele desligado — não escreve nada: deploy
 * correto tem log limpo.
 *
 * <p><b>As duas divergências, e por que nenhuma pode ficar de fora.</b> {@code https} com o cookie
 * <b>sem</b> {@code Secure} é o defeito que abriu a #74: o cookie viaja em texto claro em qualquer
 * navegação {@code http} até a borda redirecionar, e o app funciona — ninguém percebe. {@code http}
 * com o cookie declarado <b>com</b> {@code Secure} é o contrário, e o sintoma é pior: o navegador
 * guarda cookie {@code Secure} em {@code localhost} — que ele trata como origem confiável — mas
 * <b>descarta em qualquer outro host</b>, e ali o login não completa e toda chamada responde 401. O
 * app simplesmente não funciona, e sem este aviso o log não diria por quê. É o estado de quem ligou
 * o flag por engano — ou de quem obedeceu ao primeiro aviso num ambiente onde o {@code
 * X-Forwarded-Proto} chegou forjado.
 *
 * <p><b>O que ele não faz.</b> Não liga nem desliga o flag sozinho, e não recusa a subida: aviso,
 * não portão. E <b>não manda mexer de olhos fechados</b>, porque o {@code X-Forwarded-Proto}
 * atravessa o nginx vindo de quem chama quando ninguém na frente o sanear (o {@code map} do {@code
 * nginx.conf.template} só usa {@code $scheme} quando o header chega vazio). Os dois textos pedem a
 * mesma conferência: o esquema observado é afirmação de quem está na frente, não fato.
 *
 * <p><b>Privacidade.</b> O aviso carrega o nome de uma variável de ambiente e mais nada. Nenhum
 * endereço, nenhuma rota, nenhum identificador de sessão — a promessa de {@code
 * docs/11-privacidade.md} vale para o log também (D50).
 */
@Component
public class CookieSecureTopology {

    private static final Logger log = LoggerFactory.getLogger(CookieSecureTopology.class);

    /** A variável que conserta a divergência; o aviso existe para nomeá-la. */
    public static final String VARIAVEL = "FOS_COOKIE_SECURE";

    /**
     * De quanto em quanto tempo a divergência volta ao log.
     *
     * <p>Mesma janela do {@link ProxyTopology}, pelo mesmo motivo: repetir a cada requisição
     * viraria ruído que ninguém lê, e avisar uma vez só sumiria do log de quem for olhar horas
     * depois.
     */
    static final Duration JANELA = Duration.ofHours(1);

    /** O que a configuração diz. {@code null} é "não marque", que é o que o Tomcat faz. */
    private final boolean declarado;

    private final Clock clock;

    /** Quando o último aviso saiu. Nulo é "nada ainda". */
    private final AtomicReference<Instant> ultimo = new AtomicReference<>();

    public CookieSecureTopology(ServerProperties properties, Clock clock) {
        this.declarado =
                Boolean.TRUE.equals(properties.getServlet().getSession().getCookie().getSecure());
        this.clock = clock;
    }

    /**
     * Confere o esquema que chegou contra o flag declarado e avisa quando os dois não batem, em
     * qualquer das duas direções.
     *
     * @param tlsNaBorda o {@code isSecure()} da requisição, já embrulhada pelo {@code
     *     ForwardedHeaderFilter}
     */
    public void observe(boolean tlsNaBorda) {
        if (tlsNaBorda == declarado) {
            return;
        }
        if (!deveAvisar()) {
            return;
        }
        if (tlsNaBorda) {
            log.warn(
                    "A requisição chegou por https e o cookie de sessão está declarado sem Secure:"
                            + " ele viaja em texto claro em qualquer navegação http até a borda"
                            + " redirecionar — e o redirecionamento é a resposta, quando o cookie já"
                            + " foi. Quem conserta é {}=true. Confirme antes que o TLS é seu: este"
                            + " esquema vem do X-Forwarded-Proto, que atravessa o proxy vindo de quem"
                            + " chama quando ninguém na frente o saneia, e ligar o flag onde não há"
                            + " TLS de verdade derruba a sessão de todo host que não seja localhost",
                    VARIAVEL);
            return;
        }
        log.warn(
                "A requisição chegou por http e o cookie de sessão está declarado com Secure: o"
                        + " navegador descarta esse cookie em todo host que não seja localhost, e"
                        + " ali não há sessão nenhuma — o login não completa e toda chamada responde"
                        + " 401. Se este ambiente não tem TLS na borda, quem conserta é {}=false. Se"
                        + " tem, esta requisição não passou por ela: o esquema vem do"
                        + " X-Forwarded-Proto, e quem está na frente é que responde por ele",
                VARIAVEL);
    }

    /** Um aviso por janela. */
    private boolean deveAvisar() {
        Instant agora = clock.instant();
        Instant anterior = ultimo.get();
        if (anterior != null && anterior.isAfter(agora.minus(JANELA))) {
            return false;
        }
        return ultimo.compareAndSet(anterior, agora);
    }
}
