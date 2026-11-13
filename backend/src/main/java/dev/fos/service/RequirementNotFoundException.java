package dev.fos.service;

/**
 * Requisito inexistente — ou de outra conta. Vira 404 em {@link dev.fos.web.ApiExceptionHandler}.
 *
 * <p>Um caso só, como o da sessão do diário: distinguir "não existe" de "não é seu" transformaria a
 * rota em consulta de quais ids existem no banco.
 */
public class RequirementNotFoundException extends RuntimeException {

    public RequirementNotFoundException(Long id) {
        super("Requisito não encontrado: " + id);
    }
}
