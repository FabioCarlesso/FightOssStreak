package dev.fos.service;

import dev.fos.config.FosProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A topologia <b>declarada</b> ({@code fos.proxy.trusted-hops}) confrontada com a <b>observada</b>
 * na requisição — em um lugar só (#97).
 *
 * <p><b>Por que existe.</b> O conserto da #77/#96 só funciona com o código e a variável {@code
 * FOS_PROXY_TRUSTED_HOPS} no mesmo deploy, e eles entram por caminhos diferentes: o código por PR
 * com CI, a variável por clique no painel da plataforma. Na subida da #96 a variável não entrou, o
 * backend rodou com o default {@code 1} do Compose, e nada apareceu — {@code 1} é valor válido,
 * então não houve exceção nem log. O efeito medido foi dos dois lados errado ao mesmo tempo:
 * visitantes distintos colidindo num punhado de endereços de infraestrutura (o peer do nginx
 * <b>rotaciona</b> na Railway) e quem trocasse de conexão ganhando contador novo. Este componente é
 * o que faz esse deploy se anunciar em vez de degradar em silêncio — o mesmo remédio que a regra 7
 * do {@code CLAUDE.md} deu ao renome de job de CI com o {@code scripts/verificar-ruleset.mjs}.
 *
 * <p><b>O que ele não faz.</b> Não adivinha a topologia nem corrige o número: quantos saltos pular
 * continua sendo <em>declarado</em>, porque deduzir de dentro da requisição é exatamente o que o
 * {@code getRemoteAddr()} fazia. E não recusa a subida: o app tem que atender, e degradar com aviso
 * é melhor que não atender. Ele só conta elementos e avisa.
 *
 * <p><b>Privacidade.</b> O aviso carrega dois números e o nome de uma variável de ambiente. Nenhum
 * endereço entra no texto — contar elementos não é registrar endereço, e a promessa de {@code
 * docs/11-privacidade.md} continua de pé.
 */
@Component
public class ProxyTopology {

    private static final Logger log = LoggerFactory.getLogger(ProxyTopology.class);

    /** A variável que conserta a divergência; o aviso existe para nomeá-la. */
    public static final String VARIAVEL = "FOS_PROXY_TRUSTED_HOPS";

    /**
     * De quanto em quanto tempo a divergência volta ao log.
     *
     * <p>Repetir a cada requisição viraria ruído que ninguém lê; avisar uma vez só e nunca mais
     * some do log de quem for olhar horas depois. Uma hora é o meio-termo: some do fluxo normal,
     * sobrevive ao deploy.
     *
     * <p>É uma janela, e não um "já avisei este número", porque <b>o tamanho da cadeia é em parte
     * escrito por quem chama</b>: onde não há borda que saneie o {@code X-Forwarded-For}, o {@code
     * $proxy_add_x_forwarded_for} do nginx acrescenta ao valor recebido, e um cliente que varie
     * quantos elementos manda variaria também a contagem. Tratar cada número novo como notícia
     * entregaria o log para ele.
     */
    static final Duration JANELA = Duration.ofHours(1);

    private final int declarados;
    private final Clock clock;

    /** Quando o último aviso saiu. Nulo é "nada ainda". */
    private final AtomicReference<Instant> ultimo = new AtomicReference<>();

    public ProxyTopology(FosProperties properties, Clock clock) {
        this.declarados = properties.proxy().trustedHops();
        this.clock = clock;
    }

    /** Quantos saltos a configuração diz que a requisição atravessa até chegar aqui. */
    public int declaredHops() {
        return declarados;
    }

    /**
     * Confere a cadeia que chegou e avisa quando ela não bate com o declarado.
     *
     * <p>Os dois lados da divergência importam, e por motivos diferentes. Cadeia <b>mais curta</b>
     * que o configurado é {@code trusted-hops} grande demais — ou alguém alcançando o nginx sem
     * passar pela borda que se supôs na frente: o {@link ClientIp} cai no último elemento, que é o
     * extremo seguro, mas o comportamento seguro é justamente o que esconde o erro. Cadeia <b>mais
     * longa</b> é proxy novo na frente (uma CDN, um salto a mais na plataforma), e aí a chave passa
     * a ser um endereço de infraestrutura, o mesmo para todo mundo que entra por ele. Nos dois
     * casos quem conserta é a variável, e é por isso que o aviso a nomeia.
     *
     * <p><b>Só o lado curto ganha um número para copiar.</b> Cadeia mais curta que o declarado é
     * topologia, e nada além dela pode encurtá-la — o valor observado é o conserto. Mais longa pode
     * ser proxy novo na frente <em>ou</em> alguém escrevendo {@code X-Forwarded-For} onde não há
     * borda que saneie, e mandar ajustar a variável para o que chegou seria mandar entregar a chave
     * a quem chama. Aí o aviso pede conferência da topologia, não obediência.
     *
     * @param observados quantos elementos vieram na cadeia desta requisição
     */
    public void observe(int observados) {
        if (observados <= 0 || observados == declarados) {
            return;
        }
        if (!deveAvisar()) {
            return;
        }
        if (observados < declarados) {
            log.warn(
                    "Topologia observada não bate com a declarada: a cadeia de {} chegou com {}"
                            + " elemento(s) e {} está em {}. Enquanto não baterem, o endereço que"
                            + " chaveia os freios sai do salto mais próximo e todo mundo divide a"
                            + " chave — ajuste {} para {}",
                    ClientIp.HEADER,
                    observados,
                    VARIAVEL,
                    declarados,
                    VARIAVEL,
                    observados);
            return;
        }
        log.warn(
                "Topologia observada não bate com a declarada: a cadeia de {} chegou com {}"
                        + " elemento(s) e {} está em {}. Pode ser proxy novo na frente — ou quem chama"
                        + " escrevendo X-Forwarded-For onde nenhuma borda saneia. Confira a topologia"
                        + " antes de mexer em {}: subir o número sem conferir é entregar a chave dos"
                        + " freios a quem chama",
                ClientIp.HEADER,
                observados,
                VARIAVEL,
                declarados,
                VARIAVEL);
    }

    /** Um aviso por janela, venha a divergência de onde vier. */
    private boolean deveAvisar() {
        Instant agora = clock.instant();
        Instant anterior = ultimo.get();
        if (anterior != null && anterior.isAfter(agora.minus(JANELA))) {
            return false;
        }
        return ultimo.compareAndSet(anterior, agora);
    }
}
