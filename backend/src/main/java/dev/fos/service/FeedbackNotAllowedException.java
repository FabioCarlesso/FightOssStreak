package dev.fos.service;

/**
 * Conta de demonstração tentando mandar feedback. Vira 403 em {@link
 * dev.fos.web.ApiExceptionHandler}.
 *
 * <p>A demo (D39, #62) não tem identidade própria e é descartável em duas horas: um feedback dela
 * não tem para quem responder nem por quanto tempo vale — mesmo raciocínio que já faz {@code
 * AccountService#isOwner} responder falso para ela mesmo quando o modelo está em {@code
 * owner-emails}.
 */
public class FeedbackNotAllowedException extends RuntimeException {

    public FeedbackNotAllowedException() {
        super("Conta de demonstração não pode enviar feedback.");
    }
}
