package dev.fos.config;

import dev.fos.email.EmailConfigured;
import dev.fos.service.EmailAccessService;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Quando o resumo da fila sai (#54). O <em>que</em> ele contém é do {@link EmailAccessService}.
 *
 * <p><b>Das 10h às 22h, de hora em hora, em horário de Brasília</b> — treze janelas por dia. O
 * limite é de produto, não técnico: um pedido de acesso não justifica acordar ninguém, e um atraso
 * de até uma hora dentro do dia não custa nada a quem está esperando liberação. Pedido que chega às
 * 22h30 entra no resumo das 10h do dia seguinte.
 *
 * <p><b>Sem credencial de envio nada disso é registrado</b>, e é por isso que o
 * {@code @EnableScheduling} mora aqui em vez de na aplicação: ligado incondicionalmente, o cron
 * rodaria em dev e em CI, onde não existe o que enviar. Mesma regra dos provedores de login — a
 * aplicação sobe sem segredo nenhum.
 *
 * <p><b>Uma instância só.</b> {@code @Scheduled} dispara em cada processo; com duas réplicas o dono
 * receberia o resumo em duplicata. Hoje o deploy é de instância única (D22) e isso basta. Se um dia
 * houver mais de uma, o conserto é uma trava no banco, não desligar o resumo.
 */
@Configuration
@EnableScheduling
@Conditional(EmailConfigured.class)
class QueueNoticeSchedule {

    /** Minuto zero, de hora em hora, entre 10h e 22h inclusive. */
    private static final String DE_HORA_EM_HORA = "0 0 10-22 * * *";

    private final EmailAccessService acesso;

    QueueNoticeSchedule(EmailAccessService acesso) {
        this.acesso = acesso;
    }

    @Scheduled(cron = DE_HORA_EM_HORA, zone = EmailAccessService.FUSO_BRASIL)
    void resumoDaFila() {
        acesso.notifyOwnersOfQueue();
    }
}
