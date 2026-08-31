package dev.fos.service;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * O endereço de onde a requisição veio — em um lugar só, e escolhido pela infraestrutura.
 *
 * <p><b>Por que não {@code getRemoteAddr()}</b> (a #77): com {@code forward-headers-strategy:
 * framework}, o {@code ForwardedHeaderFilter} do Spring troca o endereço do peer pelo
 * <b>primeiro</b> elemento do {@code X-Forwarded-For} — e o primeiro elemento é justamente o que o
 * cliente escreveu, porque o nginx <em>acrescenta</em> ao valor recebido em vez de substituí-lo.
 * Trocar o header a cada requisição zerava o freio de senha, o da demonstração e a chave de visita
 * da coleta de uso.
 *
 * <p><b>De onde o endereço vem agora:</b> do {@code X-Fos-Forwarded-For}, que o nginx escreve em
 * toda requisição proxiada (ver {@code web/nginx.conf.template}) com a cadeia inteira — o que
 * chegou de fora <em>mais</em> o peer que ele mesmo enxergou, sempre no fim. O cliente não escolhe
 * o valor porque o nginx sobrescreve o header; o que ele escreve fica na cabeça da lista, e a
 * leitura conta a partir do <b>fim</b>. Header próprio, e não o {@code X-Forwarded-For}, porque o
 * {@code ForwardedHeaderFilter} <em>remove</em> os {@code X-Forwarded-*} da requisição antes de
 * qualquer código da aplicação vê-los: não há filtro que rode antes dele (ele é registrado em
 * {@code Ordered.HIGHEST_PRECEDENCE}).
 *
 * <p><b>Quantos elementos pular é configuração</b> ({@code fos.proxy.trusted-hops}), porque depende
 * da topologia e não do código: no Compose só o nginx está na frente e o último elemento é quem
 * navega. Na Railway o número é <b>3</b>, medido no log de acesso do nginx em produção: a borda da
 * plataforma descarta o {@code X-Forwarded-For} de quem chama e entrega {@code <visitante>, <nó de
 * borda>}, e o nginx anexa o próprio peer — o visitante é o antepenúltimo. Errar o número não abre
 * a porta em silêncio: com número pequeno demais todo mundo cai na mesma chave e o freio recusa
 * gente legítima, que é ruído visível, e não bypass — mas é degradação, então a variável entra no
 * mesmo deploy que este código.
 *
 * <p>Sem o header — dev direto no {@code :8080}, teste, qualquer coisa que não passe pelo nginx —
 * vale o peer de verdade da conexão, lido do request <b>de baixo</b> dos wrappers. O {@code
 * getRemoteAddr()} do wrapper é o que o {@code ForwardedHeaderFilter} extraiu do header do cliente;
 * o de baixo é a conexão TCP, que ninguém escreve.
 *
 * <p>O que este componente <b>não</b> faz, e não deve passar a fazer: devolver o IP para alguém
 * guardar. Ele existe para ser consumido e descartado dentro da requisição — derivar país, compor
 * hash, contar tentativa. Não há coluna de IP em tabela nenhuma (D50), e {@code
 * docs/11-privacidade.md} promete isso por escrito.
 */
@Component
public class ClientIp {

    /**
     * O header que o nginx escreve, e o único em que o app confia.
     *
     * <p>Nome próprio de propósito: o {@code X-Forwarded-For} não sobrevive ao {@code
     * ForwardedHeaderFilter}, e o {@code X-Real-IP} carrega um endereço só — o peer do nginx, que
     * atrás da borda da plataforma é a mesma máquina para todo mundo.
     */
    public static final String HEADER = "X-Fos-Forwarded-For";

    private final ProxyTopology topology;
    private final int saltosConfiaveis;

    public ClientIp(ProxyTopology topology) {
        this.topology = topology;
        this.saltosConfiaveis = topology.declaredHops();
    }

    /** Nunca nulo: requisição sem endereço conhecido vira string vazia, não NPE. */
    public String of(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String daBorda = doFimDaCadeia(request.getHeader(HEADER));
        return daBorda.isEmpty() ? peer(request) : daBorda;
    }

    /**
     * Conta do fim para o começo, pulando os saltos que são nossos.
     *
     * <p>Cadeia mais curta que o configurado é topologia diferente da declarada: {@code
     * trusted-hops} maior que a realidade, ou alguém alcançando o nginx sem passar pela borda que
     * se supôs na frente. Nesse caso vale o <b>último</b> elemento, e não o primeiro — o último foi
     * escrito pelo salto mais próximo, que é infraestrutura nossa; o primeiro é o que quem chama
     * escreveu no {@code X-Forwarded-For}, porque o {@code $proxy_add_x_forwarded_for} acrescenta
     * ao valor recebido. Cair no primeiro devolveria a chave para o cliente exatamente no cenário
     * de configuração errada, e em silêncio — o defeito que a #77 existe para eliminar. Com o
     * último, todo mundo atrás daquele salto divide a chave: o freio recusa gente legítima, que é
     * ruído visível.
     *
     * <p>O comportamento seguro, porém, é o que esconde o erro: a chave sai de um lugar que não é o
     * declarado e nada aparece. Quem conta essa história é o {@link ProxyTopology}, chamado aqui
     * com o tamanho da cadeia — o {@code WARN} é a única coisa que a #97 acrescentou, e a escolha
     * do elemento continua exatamente a mesma.
     *
     * <p>Errar o número <b>para baixo</b> já era assim (a chave vira um endereço de
     * infraestrutura); esta linha é o que faz errar <b>para cima</b> degradar do mesmo jeito, em
     * vez de reabrir a porta.
     */
    private String doFimDaCadeia(String cadeia) {
        if (cadeia == null || cadeia.isBlank()) {
            return "";
        }
        String[] saltos = cadeia.split(",");
        topology.observe(saltos.length);
        int posicao = saltos.length - saltosConfiaveis;
        return saltos[posicao < 0 ? saltos.length - 1 : posicao].trim();
    }

    /** O peer da conexão, por baixo de todo wrapper que tenha reescrito o endereço no caminho. */
    private static String peer(HttpServletRequest request) {
        ServletRequest atual = request;
        while (atual instanceof ServletRequestWrapper wrapper) {
            atual = wrapper.getRequest();
        }
        String ip = atual.getRemoteAddr();
        return ip == null ? "" : ip;
    }
}
