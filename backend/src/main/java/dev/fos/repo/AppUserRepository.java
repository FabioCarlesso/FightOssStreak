package dev.fos.repo;

import dev.fos.model.AccessStatus;
import dev.fos.model.AppUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** Fila de solicitações, da mais antiga para a mais nova. */
    List<AppUser> findByAccessStatusOrderByRequestedAtAsc(AccessStatus status);
}
