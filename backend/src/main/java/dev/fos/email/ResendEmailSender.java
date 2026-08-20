package dev.fos.email;

import dev.fos.config.FosProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
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
@Conditional(ResendEmailSender.EmailConfigured.class)
class ResendEmailSender implements EmailSender {

    /**
     * Verdadeiro só com os dois valores preenchidos.
     *
     * <p>Precisa ser {@link Condition} e não {@code @ConditionalOnProperty}: a variável de ambiente
     * ausente vira string <em>vazia</em> pelo default do {@code application.yml}, e para o
     * {@code @ConditionalOnProperty} vazio é "presente". O bean nascia sem credencial e tentava
     * falar com a API de verdade — inclusive de dentro do teste, que foi como isto apareceu.
     */
    static class EmailConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return new FosProperties.Email(
                            context.getEnvironment().getProperty("fos.email.api-key"),
                            context.getEnvironment().getProperty("fos.email.from"))
                    .isConfigured();
        }
    }

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
