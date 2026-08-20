package dev.fos.repo;

import dev.fos.model.UserIdentity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    /** A chave real da identidade — ver o javadoc de {@link UserIdentity}. */
    Optional<UserIdentity> findByProviderAndProviderSubject(
            String provider, String providerSubject);

    List<UserIdentity> findByUserId(Long userId);

    List<UserIdentity> findByUserIdIn(List<Long> userIds);

    void deleteByUserId(Long userId);
}
