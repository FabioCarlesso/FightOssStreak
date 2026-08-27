package dev.fos.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * O endereço de onde a requisição veio — em um lugar só.
 *
 * <p>Ponto único de propósito, e não por elegância: a #77 ainda está aberta, e enquanto estiver, o
 * {@code forward-headers-strategy: framework} faz o {@code getRemoteAddr()} devolver o que veio no
 * {@code X-Forwarded-For} — que qualquer cliente pode escrever. Isso é o que hoje torna forjáveis o
 * freio de tentativas, o país derivado (#84) e a chave de visita. Quando a #77 for fechada, o
 * conserto é <b>aqui</b>, e não em quatro chamadas espalhadas.
 *
 * <p>O que este método <b>não</b> faz, e não deve passar a fazer: devolver o IP para alguém
 * guardar. Ele existe para ser consumido e descartado dentro da requisição — derivar país, compor
 * hash, contar tentativa. Não há coluna de IP em tabela nenhuma (D50), e {@code
 * docs/11-privacidade.md} promete isso por escrito.
 */
public final class ClientIp {

    private ClientIp() {}

    /** Nunca nulo: requisição sem endereço conhecido vira string vazia, não NPE. */
    public static String of(HttpServletRequest request) {
        String ip = request == null ? null : request.getRemoteAddr();
        return ip == null ? "" : ip;
    }
}
