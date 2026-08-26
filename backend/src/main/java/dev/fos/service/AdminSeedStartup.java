package dev.fos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Aplica a semente de administração quando a aplicação sobe (D49).
 *
 * <p>Bean separado pelo mesmo motivo do {@code CurriculumStartupSync}: chamar o método de dentro do
 * próprio serviço seria auto-invocação, o proxy do Spring não se aplicaria e ele rodaria <em>sem
 * transação</em>.
 *
 * <p>É esta subida que faz {@code FOS_OWNER_EMAILS} continuar sendo a saída de emergência depois
 * que o papel virou dado: ambiente que ficou sem nenhum administrador — porque o banco é novo, ou
 * porque a última conta de administração foi excluída pelo titular — se conserta preenchendo a
 * variável e reiniciando, sem {@code psql}.
 */
@Component
class AdminSeedStartup {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedStartup.class);

    private final AccountService accounts;

    AdminSeedStartup(AccountService accounts) {
        this.accounts = accounts;
    }

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady() {
        accounts.seedAdmins();
        log.debug("Semente de administração aplicada (fos.auth.owner-emails)");
    }
}
