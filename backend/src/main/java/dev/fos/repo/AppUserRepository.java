package dev.fos.repo;

import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * A conta dona de um e-mail verificado (#81).
     *
     * <p>É a consulta que faz identidade nova se anexar à conta que já existe, em vez de criar a
     * segunda conta vazia que a falta de merge por e-mail (D36) produziria com senha própria.
     */
    Optional<AppUser> findByPrimaryEmail(String primaryEmail);

    /** Fila de solicitações, da mais antiga para a mais nova. */
    List<AppUser> findByAccessStatusOrderByRequestedAtAsc(AccessStatus status);

    /** Demonstrações vencidas — a varredura preguiçosa da criação (#62) come daqui. */
    List<AppUser> findByDemoExpiresAtLessThan(Instant instant);

    /** Demonstrações vivas, para o teto de simultâneas. */
    long countByDemoExpiresAtIsNotNull();
}
