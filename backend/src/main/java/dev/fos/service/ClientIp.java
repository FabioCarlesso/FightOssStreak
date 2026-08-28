package dev.fos.service;

import dev.fos.config.FosProperties;
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
 * navega; na Railway a borda da plataforma anexa o visitante antes, e o nginx anexa o endereço dela
 * depois — o visitante é o penúltimo. Errar o número não abre a porta em silêncio: com número
 * pequeno demais todo mundo cai na mesma chave e o freio recusa gente legítima, que é ruído
 * visível, e não bypass.
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

    private final int saltosConfiaveis;

    public ClientIp(FosProperties properties) {
        this.saltosConfiaveis = properties.proxy().trustedHops();
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
     * <p>Cadeia mais curta que o configurado é topologia diferente da declarada — nesse caso vale o
     * primeiro elemento, que é o mais distante da aplicação que existe ali. É o extremo seguro: no
     * pior caso todo mundo divide a chave.
     */
    private String doFimDaCadeia(String cadeia) {
        if (cadeia == null || cadeia.isBlank()) {
            return "";
        }
        String[] saltos = cadeia.split(",");
        return saltos[Math.max(0, saltos.length - saltosConfiaveis)].trim();
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
