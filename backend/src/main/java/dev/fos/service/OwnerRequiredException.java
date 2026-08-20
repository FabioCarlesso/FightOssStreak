package dev.fos.service;

/**
 * Rota de administração pedida por conta que não é dona. Vira 403 em {@link
 * dev.fos.web.ApiExceptionHandler}.
 *
 * <p>Ser dono é ter e-mail verificado em {@code fos.auth.owner-emails} — uma checagem contra
 * configuração, não um sistema de papéis (#24 põe roles fora de escopo).
 */
public class OwnerRequiredException extends RuntimeException {

    public OwnerRequiredException() {
        super("Rota restrita ao dono do app.");
    }
}
