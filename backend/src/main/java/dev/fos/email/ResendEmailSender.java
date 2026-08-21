package dev.fos.email;

import dev.fos.config.FosProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envio pelo Resend, por HTTP.
 *
 * <p>Chamada direta com o {@code RestClient} que já vem no projeto, em vez de um SDK: é um POST com
 * quatro campos, e uma dependência a mais custaria mais que o que economiza.
 *
 * <p>O bean só existe com chave <em>e</em> remetente preenchidos — ver {@link EmailSender}.
 */
@Component
@Conditional(EmailConfigured.class)
class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final String ENDPOINT = "https://api.resend.com/emails";

    private final RestClient client;
    private final FosProperties.Email config;

    ResendEmailSender(RestClient.Builder builder, FosProperties properties) {
        this.config = properties.email();
        this.client = builder.build();
    }

    @Override
    public void send(String to, String subject, String body) {
        client.post()
                .uri(ENDPOINT)
                .header("Authorization", "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("from", config.from(), "to", to, "subject", subject, "text", body))
                .retrieve()
                .toBodilessEntity();
        // O endereço não entra no log: é dado pessoal, e saber que "um e-mail saiu" já basta para
        // depurar. Ver docs/11-privacidade.md.
        log.info("E-mail de entrada enviado");
    }
}
