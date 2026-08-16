package dev.fos.curriculum;

/**
 * Currículo inválido.
 *
 * <p>É deliberadamente fatal na subida: um currículo com ciclo ou referência quebrada produziria
 * nós impossíveis de desbloquear, e falhar rápido é melhor do que descobrir isso em runtime.
 */
public class CurriculumException extends RuntimeException {

    public CurriculumException(String message) {
        super(message);
    }

    public CurriculumException(String message, Throwable cause) {
        super(message, cause);
    }
}
