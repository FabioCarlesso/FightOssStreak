package dev.fos.service;

import dev.fos.model.AccessStatus;

/**
 * Sessão válida, conta ainda não liberada. Vira 403 em {@link dev.fos.web.ApiExceptionHandler}.
 *
 * <p>403 e não 401 de propósito: 401 mandaria a web de volta para a tela de login, que é exatamente
 * o que a pessoa acabou de fazer — o resultado seria um looping. O código no corpo
 * (`acesso_pendente` / `acesso_recusado`) é o que diz qual tela mostrar.
 */
public class AccessNotGrantedException extends RuntimeException {

    private final AccessStatus status;

    public AccessNotGrantedException(AccessStatus status) {
        super(
                status == AccessStatus.RECUSADO
                        ? "Solicitação de acesso recusada."
                        : "Solicitação de acesso ainda não aprovada.");
        this.status = status;
    }

    public AccessStatus getStatus() {
        return status;
    }

    public String code() {
        return status == AccessStatus.RECUSADO ? "acesso_recusado" : "acesso_pendente";
    }
}
