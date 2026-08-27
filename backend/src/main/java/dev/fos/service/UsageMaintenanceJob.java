package dev.fos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * O job diário da coleta: fecha os dias e expurga o cru (#84, D50).
 *
 * <p><b>Sem a condição de credencial de e-mail</b>, e isso é o ponto. A D38 amarrou o
 * {@code @EnableScheduling} ao envio configurado porque o único agendamento da época era um resumo
 * por e-mail. Herdar aquela condição aqui faria todo ambiente sem provedor de envio — dev, CI, e
 * qualquer instalação que só use login social — nunca agregar nada e nunca expurgar nada, com a
 * tabela crua crescendo para sempre em silêncio. Ver {@code SchedulingConfig}.
 *
 * <p>De madrugada e em horário quebrado: o dia que ele fecha é o de ontem, então rodar cedo demais
 * depois da virada não muda nada, e um minuto qualquer evita competir com o topo da hora.
 */
@Component
public class UsageMaintenanceJob {

    private static final Logger log = LoggerFactory.getLogger(UsageMaintenanceJob.class);

    private final UsageAggregator aggregator;

    public UsageMaintenanceJob(UsageAggregator aggregator) {
        this.aggregator = aggregator;
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void run() {
        try {
            int fechados = aggregator.aggregatePending();
            int apagados = aggregator.purge();
            if (fechados > 0 || apagados > 0) {
                log.info(
                        "Coleta de uso: {} dias agregados, {} eventos expurgados",
                        fechados,
                        apagados);
            }
        } catch (RuntimeException falha) {
            // O agendador do Spring engole a exceção e para de logar depois da primeira; um job de
            // métrica que falha em silêncio é como se descobre três meses depois que não há dado.
            log.warn("Manutenção da coleta de uso falhou nesta execução", falha);
        }
    }
}
