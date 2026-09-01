package dev.fos.service;

import dev.fos.model.AppStart;
import dev.fos.repo.AppStartRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Uma linha por subida da aplicação (#86).
 *
 * <p>É a peça mais barata do monitoramento e a que responde a pergunta mais difícil de responder
 * depois: <em>o app reiniciou?</em> O log da plataforma rotaciona, o histórico de deploy só mostra
 * as subidas que alguém pediu, e um restart por falta de memória às 3h não deixa rastro em lugar
 * nenhum. Com esta tabela, o painel mostra três subidas na madrugada e a suspeita vira fato.
 *
 * <p>No {@link ApplicationReadyEvent}, e não no construtor de um bean: o que interessa registrar é
 * o instante em que o app passou a <b>atender</b>, e não o em que o contexto começou a montar.
 *
 * <p>Falha aqui não derruba a subida: um monitoramento que impede o app de subir é pior que a
 * ausência dele.
 */
@Component
public class AppStartRecorder {

    private static final Logger log = LoggerFactory.getLogger(AppStartRecorder.class);

    private static final int MAX_PERFIS = 120;

    private final AppStartRepository starts;
    private final Environment environment;
    private final Clock clock;

    public AppStartRecorder(AppStartRepository starts, Environment environment, Clock clock) {
        this.starts = starts;
        this.environment = environment;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registrar() {
        try {
            starts.save(new AppStart(Instant.now(clock), perfis()));
        } catch (RuntimeException falha) {
            log.warn("Subida da aplicação não registrada no histórico de saúde", falha);
        }
    }

    private String perfis() {
        String[] ativos = environment.getActiveProfiles();
        String texto = ativos.length == 0 ? "default" : String.join(",", ativos);
        return texto.length() > MAX_PERFIS ? texto.substring(0, MAX_PERFIS) : texto;
    }
}
