package dev.fos.repo;

import dev.fos.model.PasswordCredential;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PasswordCredentialRepository extends JpaRepository<PasswordCredential, Long> {

    Optional<PasswordCredential> findByIdentityId(Long identityId);

    /**
     * Apaga a credencial de várias identidades de uma vez.
     *
     * <p>É por identidade, e não por conta, porque é a identidade que tem senha: a exclusão da
     * conta ({@code AccountService.delete}) passa aqui as identidades dela antes de apagá-las, e
     * esquecer este passo daria violação de FK, não perda silenciosa.
     */
    @Transactional
    void deleteByIdentityIdIn(List<Long> identityIds);
}
