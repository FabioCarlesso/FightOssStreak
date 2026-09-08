package dev.fos.service;

/**
 * Sessão inexistente — ou de outra conta. Vira 404 em {@link dev.fos.web.ApiExceptionHandler}.
 *
 * <p>Uma exceção só para os dois casos, de propósito: distinguir "não existe" de "não é sua"
 * transformaria a rota em consulta de quais ids existem no banco.
 */
public class TrainingSessionNotFoundException extends RuntimeException {

    public TrainingSessionNotFoundException(Long id) {
        super("Sessão de treino não encontrada: " + id);
    }
}
