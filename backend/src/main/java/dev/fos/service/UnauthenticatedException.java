package dev.fos.service;

/**
 * Requisição sem sessão válida. Vira 401 em {@link dev.fos.web.ApiExceptionHandler}.
 *
 * <p>Nunca cria usuário: com login, conta é consequência de um login bem-sucedido, não de uma
 * consulta que não achou ninguém (#24).
 */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("Requisição sem sessão autenticada.");
    }
}
