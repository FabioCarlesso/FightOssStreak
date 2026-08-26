package dev.fos.service;

/**
 * Rota de administração pedida por conta que não é dona. Vira 403 em {@link
 * dev.fos.web.ApiExceptionHandler}.
 *
 * <p>Administrar é ter {@code app_user.role = ADMIN} (D49) — papel em tabela, com dois valores e
 * sem permissão por recurso. A configuração {@code fos.auth.owner-emails} continua existindo como
 * semente da subida, não como fonte da verdade.
 */
public class OwnerRequiredException extends RuntimeException {

    public OwnerRequiredException() {
        super("Rota restrita ao dono do app.");
    }
}
