package dev.fos.email;

import dev.fos.config.FosProperties;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envio pelo Resend, por HTTP.
 *
 * <p>Chamada direta com o {@code RestClient} que já vem no projeto, em vez de um SDK: é um POST com
 * quatro campos, e uma dependência a mais custaria mais que o que economiza.
 *
 * <p>O bean só existe com chave <em>e</em> remetente preenchidos — ver {@link EmailSender}.
 *
 * <p><b>Os timeouts não são zelo: sem eles esta classe trava o resumo da fila.</b> O default do
 * {@code RestClient} é esperar para sempre, e o resumo (#54) roda num agendador de uma thread só —
 * uma conexão pendurada aqui não devolve nunca, e todas as janelas seguintes do dia deixam de
 * acontecer, em silêncio. Falhar rápido é o comportamento certo: o resumo não marca a fila como
 * avisada quando o envio falha, então a janela seguinte tenta de novo.
 */
@Component
@Conditional(EmailConfigured.class)
class ResendEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);
    private static final String ENDPOINT = "https://api.resend.com/emails";

    /** Curtos de propósito: ninguém está esperando na frente disto, e travar custa caro. */
    private static final Duration CONECTAR = Duration.ofSeconds(5);

    private static final Duration LER = Duration.ofSeconds(10);

    private final RestClient client;
    private final FosProperties.Email config;

    ResendEmailSender(RestClient.Builder builder, FosProperties properties) {
        this.config = properties.email();
        this.client = builder.requestFactory(comTimeout()).build();
    }

    private static ClientHttpRequestFactory comTimeout() {
        return ClientHttpRequestFactoryBuilder.detect()
                .build(
                        ClientHttpRequestFactorySettings.defaults()
                                .withConnectTimeout(CONECTAR)
                                .withReadTimeout(LER));
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
