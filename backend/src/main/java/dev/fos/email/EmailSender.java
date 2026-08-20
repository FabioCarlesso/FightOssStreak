package dev.fos.email;

/**
 * Envio de e-mail transacional.
 *
 * <p>Interface, e não a chamada direta ao provedor, por um motivo prático: sem credencial não
 * existe implementação nenhuma, e é isso que faz a entrada por e-mail simplesmente não ser
 * oferecida — em vez de aparecer na tela e falhar no clique. Mesma escolha dos provedores de login
 * (D36).
 */
public interface EmailSender {

    void send(String to, String subject, String body);
}
