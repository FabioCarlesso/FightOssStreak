package dev.fos.service;

import dev.fos.model.AccessStatus;

/**
 * Sessão válida, conta bloqueada. Vira 403 em {@link dev.fos.web.ApiExceptionHandler}.
 *
 * <p>403 e não 401 de propósito: 401 mandaria a web de volta para a tela de login, que é exatamente
 * o que a pessoa acabou de fazer — o resultado seria um looping. O código no corpo ({@code
 * acesso_recusado}) é o que diz qual tela mostrar.
 *
 * <p>Quem produz {@link AccessStatus#RECUSADO} é o bloqueio de quem administra (#90). Entre a D48 e
 * ela este caminho existia sem ninguém que o percorresse — ver o javadoc de {@code AccessStatus}.
 */
public class AccessNotGrantedException extends RuntimeException {

    public AccessNotGrantedException() {
        super("Esta conta está bloqueada.");
    }

    public String code() {
        return "acesso_recusado";
    }
}
