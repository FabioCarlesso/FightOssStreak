package dev.fos.email;

import dev.fos.config.FosProperties;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Verdadeiro só com chave <em>e</em> remetente preenchidos.
 *
 * <p>Precisa ser {@link Condition} e não {@code @ConditionalOnProperty}: a variável de ambiente
 * ausente vira string <em>vazia</em> pelo default do {@code application.yml}, e para o
 * {@code @ConditionalOnProperty} vazio é "presente". O bean nascia sem credencial e tentava falar
 * com a API de verdade — inclusive de dentro do teste, que foi como isto apareceu.
 *
 * <p>Classe própria, e não aninhada no {@link EmailSender} que a usa, porque o agendador do resumo
 * da fila (#54) precisa da mesma condição de outro pacote: sem credencial de envio não há o que
 * agendar, e ligar o {@code @EnableScheduling} mesmo assim faria o cron rodar em dev e em CI.
 */
public class EmailConfigured implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return new FosProperties.Email(
                        context.getEnvironment().getProperty("fos.email.api-key"),
                        context.getEnvironment().getProperty("fos.email.from"))
                .isConfigured();
    }
}
