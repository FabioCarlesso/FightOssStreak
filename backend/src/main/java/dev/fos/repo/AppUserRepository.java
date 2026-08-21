package dev.fos.repo;

import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Fila de solicitações, da mais antiga para a mais nova. */
    List<AppUser> findByAccessStatusOrderByRequestedAtAsc(AccessStatus status);

    /** Demonstrações vencidas — a varredura preguiçosa da criação (#62) come daqui. */
    List<AppUser> findByDemoExpiresAtLessThan(Instant instant);

    /** Demonstrações vivas, para o teto de simultâneas. */
    long countByDemoExpiresAtIsNotNull();
}
