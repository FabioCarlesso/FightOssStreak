package dev.fos.repo;

import dev.fos.model.AppleCredential;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AppleCredentialRepository extends JpaRepository<AppleCredential, Long> {

    Optional<AppleCredential> findByIdentityId(Long identityId);

    List<AppleCredential> findByIdentityIdIn(List<Long> identityIds);

    @Transactional
    void deleteByIdentityIdIn(List<Long> identityIds);
}
