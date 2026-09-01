package dev.fos.service;

import dev.fos.config.FosProperties;
import dev.fos.email.EmailSender;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * O aviso que a própria aplicação consegue dar (#86).
 *
 * <p><b>O que ela não consegue avisar é que morreu</b> — alerta gerado dentro do processo é
 * exatamente o que não roda quando o processo não está de pé. Essa metade é do workflow em cron do
 * GitHub Actions ({@code .github/workflows/saude.yml}), que bate de fora. Aqui fica só o que só
 * quem está dentro enxerga: a aplicação <em>respondendo</em>, e respondendo errado.
 *
 * <p><b>Dois incidentes distintos, e de propósito.</b> Taxa de 5xx é o app errando; pico de 401/403
 * é o app recusando <em>certo</em>, e o que ele denuncia é varredura de credencial vinda de fora.
 * Somar os dois numa taxa só faria um ataque parecer um defeito.
 *
 * <p><b>Uma trava por incidente, não um alerta por janela.</b> É a lição que a D38 já tinha pago: o
 * aviso que chega a cada quinze minutos enquanto o problema dura vira filtro no Gmail em dois dias,
 * e aí o próximo incidente também não é lido. O alerta sai <b>uma vez</b>, quando a condição
 * aparece; enquanto ela durar, nada mais é enviado; quando ela passar, a trava abre — e só então um
 * incidente novo pode avisar de novo. A volta ao normal não gera e-mail: quem recebeu o primeiro
 * vai olhar o painel de qualquer forma, e um segundo e-mail dobra o ruído para dizer que acabou.
 *
 * <p><b>Sem credencial de envio não há nada disto</b>, e a aplicação sobe igual (regra 4 do {@code
 * CLAUDE.md}): sem {@link EmailSender} registrado, o método sai na primeira linha. Dev e CI rodam
 * assim.
 */
@Service
public class IncidentAlerts {

    private static final Logger log = LoggerFactory.getLogger(IncidentAlerts.class);

    private final HttpStatCollector collector;
    private final ObjectProvider<EmailSender> emailSender;
    private final FosProperties properties;

    /** As duas travas. Só o job escreve nelas, e ele é de linha única — {@code volatile} basta. */
    private volatile boolean erroAberto;

    private volatile boolean picoAberto;

    public IncidentAlerts(
            HttpStatCollector collector,
            ObjectProvider<EmailSender> emailSender,
            FosProperties properties) {
        this.collector = collector;
        this.emailSender = emailSender;
        this.properties = properties;
    }

    /** Olha a janela e decide se há incidente novo. Devolve quantos e-mails saíram. */
    public int verificar() {
        EmailSender sender = emailSender.getIfAvailable();
        if (sender == null) {
            return 0;
        }
        List<String> destinatarios = properties.auth().ownerEmails();
        if (destinatarios.isEmpty()) {
            // Sem destinatário não há alerta — e não há para quem reclamar disso.
            return 0;
        }

        FosProperties.Health config = properties.health();
        HttpStatCollector.Janela janela = collector.janela(config.windowMinutes());

        int enviados = 0;
        enviados += avaliarTaxaDeErro(sender, destinatarios, janela, config);
        enviados += avaliarPicoDeRecusa(sender, destinatarios, janela, config);
        return enviados;
    }

    /**
     * Taxa de 5xx acima do limiar, com piso de requisições.
     *
     * <p>O piso não é detalhe: sem ele, uma única requisição que falhasse às 4h da manhã seria
     * "100% de erro" — o alerta mais barulhento e menos informativo que este código conseguiria
     * produzir, e o mais rápido a ser ignorado.
     */
    private int avaliarTaxaDeErro(
            EmailSender sender,
            List<String> destinatarios,
            HttpStatCollector.Janela janela,
            FosProperties.Health config) {
        boolean acima =
                janela.requests() >= config.minRequests()
                        && janela.errorRatePercent() >= config.errorRatePercent();
        if (!acima) {
            if (erroAberto) {
                log.info("Saúde: a taxa de erro voltou abaixo do limiar; incidente encerrado");
                erroAberto = false;
            }
            return 0;
        }
        if (erroAberto) {
            return 0;
        }
        erroAberto = true;
        String corpo =
                """
                A aplicação está respondendo com erro acima do limiar.

                Janela: últimos %d minutos
                Requisições: %d
                Respostas 5xx: %d (%d%%, limiar de %d%%)

                O painel de administração mostra a rota e a hora: /admin/painel, seção Saúde.
                Este é o único e-mail deste incidente — enquanto a taxa continuar alta nada mais
                será enviado, e um alerta novo só sai depois de ela voltar ao normal."""
                        .formatted(
                                config.windowMinutes(),
                                janela.requests(),
                                janela.serverErrors(),
                                janela.errorRatePercent(),
                                config.errorRatePercent());
        return enviar(sender, destinatarios, "[FOS] Taxa de erro acima do limiar", corpo);
    }

    /**
     * Pico de 401/403 na janela.
     *
     * <p>Sem piso de requisições e sem taxa: o número absoluto <em>é</em> o sinal. Cinquenta
     * recusas em quinze minutos num app deste tamanho não é gente errando a senha.
     */
    private int avaliarPicoDeRecusa(
            EmailSender sender,
            List<String> destinatarios,
            HttpStatCollector.Janela janela,
            FosProperties.Health config) {
        if (janela.authRejects() < config.authRejects()) {
            if (picoAberto) {
                log.info("Saúde: o pico de 401/403 passou; incidente encerrado");
                picoAberto = false;
            }
            return 0;
        }
        if (picoAberto) {
            return 0;
        }
        picoAberto = true;
        String corpo =
                """
                Muitas respostas 401/403 numa janela curta.

                Janela: últimos %d minutos
                Respostas 401/403: %d (limiar de %d)
                Requisições na janela: %d

                Isto não é o app errando: é ele recusando. O padrão típico é varredura de
                credencial. O freio por origem continua valendo (docs/11-privacidade.md); se o
                número não ceder, conta abusiva se bloqueia pela tela Usuários.
                Este é o único e-mail deste incidente."""
                        .formatted(
                                config.windowMinutes(),
                                janela.authRejects(),
                                config.authRejects(),
                                janela.requests());
        return enviar(sender, destinatarios, "[FOS] Pico de respostas 401/403", corpo);
    }

    /**
     * Manda para cada destinatário, e nunca deixa a falha de um afetar o outro nem o job.
     *
     * <p>A trava já foi fechada antes de chegar aqui: envio que falha não vira reenvio na próxima
     * janela. É a escolha certa entre perder um aviso e mandar um a cada quinze minutos porque o
     * provedor de e-mail está fora — a segunda é a que estraga o canal para o incidente seguinte.
     */
    private int enviar(
            EmailSender sender, List<String> destinatarios, String assunto, String corpo) {
        int enviados = 0;
        for (String destinatario : destinatarios) {
            try {
                sender.send(destinatario, assunto, corpo);
                enviados++;
            } catch (RuntimeException falha) {
                log.warn("Alerta de saúde não enviado a um destinatário", falha);
            }
        }
        log.warn("Saúde: incidente detectado — {}", assunto);
        return enviados;
    }
}
