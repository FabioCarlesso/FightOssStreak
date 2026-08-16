package dev.fos.service;

/**
 * Nó ainda sem quiz escrito.
 *
 * <p>Não é erro de programação: a curadoria de quiz é incremental (M0 e M1 primeiro). Vira 409
 * para que o cliente mostre "quiz ainda não disponível" em vez de uma tela de erro.
 */
public class QuizUnavailableException extends RuntimeException {

    public QuizUnavailableException(String code) {
        super("Nó ainda não tem quiz escrito: " + code);
    }
}
