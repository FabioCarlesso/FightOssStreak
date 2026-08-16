package dev.fos.curriculum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Dispara a ingestão do currículo quando a aplicação sobe.
 *
 * <p>Fica em um bean separado de propósito: chamar {@code sync()} de dentro do próprio serviço
 * seria auto-invocação, o proxy do Spring não se aplicaria e o método rodaria <em>sem transação</em>.
 */
@Component
class CurriculumStartupSync {

    private static final Logger log = LoggerFactory.getLogger(CurriculumStartupSync.class);

    private final CurriculumIngestionService ingestionService;

    CurriculumStartupSync(CurriculumIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        if (!ingestionService.isSyncOnStartupEnabled()) {
            log.info("Sincronização de currículo desligada (fos.curriculum.sync-on-startup=false)");
            return;
        }
        ingestionService.sync();
    }
}
