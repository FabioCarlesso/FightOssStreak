package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.repo.AppStartRepository;
import dev.fos.repo.HttpStatHourlyRepository;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os agendamentos da saúde do site (#86): descarregar a medição, avisar, expurgar.
 *
 * <p><b>Sem a condição de credencial de e-mail</b>, pela mesma razão que o {@code
 * UsageMaintenanceJob} não a tem: a D38 amarrou o {@code @EnableScheduling} ao envio configurado
 * quando o único agendamento era um resumo por e-mail, e herdar aquela condição aqui faria todo
 * ambiente sem provedor de envio — dev, CI, instalação só com login social — <b>não ter histórico
 * nenhum</b> de requisições. Quem depende de envio é só o alerta, e ele já se cala sozinho quando
 * não há {@code EmailSender} (ver {@link IncidentAlerts}).
 *
 * <p>Descarga a cada cinco minutos, e não a cada hora: o buffer vive na memória do processo, e um
 * deploy no minuto 59 jogaria a hora inteira fora.
 */
@Component
public class SiteHealthJob {

    private static final Logger log = LoggerFactory.getLogger(SiteHealthJob.class);

    private final HttpStatCollector collector;
    private final IncidentAlerts alerts;
    private final HttpStatHourlyRepository stats;
    private final AppStartRepository starts;
    private final FosProperties.Health config;
    private final Clock clock;

    public SiteHealthJob(
            HttpStatCollector collector,
            IncidentAlerts alerts,
            HttpStatHourlyRepository stats,
            AppStartRepository starts,
            FosProperties properties,
            Clock clock) {
        this.collector = collector;
        this.alerts = alerts;
        this.stats = stats;
        this.starts = starts;
        this.config = properties.health();
        this.clock = clock;
    }

    /**
     * Grava o que foi medido e, na sequência, olha se há incidente.
     *
     * <p>Os dois juntos porque a ordem importa pouco e a cadência é a mesma — mas repare que o
     * alerta lê a <b>memória</b>, não o que acabou de ser gravado: ele pergunta pelos últimos
     * quinze minutos, e o que está no banco tem granularidade de hora.
     *
     * <p>O valor especial {@code -} desliga o agendamento, como em {@code fos.usage.cron}. Isso
     * para a gravação <em>e</em> o alerta: quem desliga a descarga não quer nem uma coisa nem
     * outra.
     */
    @Scheduled(cron = "${fos.health.cron:0 */5 * * * *}")
    public void descarregar() {
        try {
            int linhas = collector.flush();
            if (linhas > 0) {
                log.debug("Saúde: {} linhas de estatística gravadas", linhas);
            }
        } catch (RuntimeException falha) {
            // O agendador do Spring engole a exceção e para de logar depois da primeira; a
            // estatística que some em silêncio é a que ninguém descobre que sumiu.
            log.warn("Descarga das estatísticas de requisição falhou nesta execução", falha);
        }
        try {
            alerts.verificar();
        } catch (RuntimeException falha) {
            log.warn("Verificação de incidente falhou nesta execução", falha);
        }
    }

    /** A última descarga, na parada ordenada — senão todo deploy perde os minutos finais. */
    @PreDestroy
    public void descarregarNaParada() {
        try {
            collector.flush();
        } catch (RuntimeException falha) {
            log.debug("Descarga final das estatísticas não concluída", falha);
        }
    }

    /**
     * O expurgo do histórico. Hora vezes rota é pequeno, e cresce todo dia para sempre.
     *
     * <p>Nada aqui tem dado pessoal — é por isso que a retenção é generosa e não é a promessa de
     * privacidade que a define, ao contrário da tabela crua de uso (D50).
     */
    @Scheduled(cron = "${fos.health.purge-cron:0 27 3 * * *}")
    @Transactional
    public void expurgar() {
        try {
            Instant limite = Instant.now(clock).minus(Duration.ofDays(config.retentionDays()));
            int estatisticas = stats.deleteByHourStartBefore(limite);
            int subidas = starts.deleteByStartedAtBefore(limite);
            if (estatisticas > 0 || subidas > 0) {
                log.info(
                        "Expurgo de saúde: {} horas de estatística e {} subidas anteriores a {}",
                        estatisticas,
                        subidas,
                        limite);
            }
        } catch (RuntimeException falha) {
            log.warn("Expurgo das tabelas de saúde falhou nesta execução", falha);
        }
    }
}
