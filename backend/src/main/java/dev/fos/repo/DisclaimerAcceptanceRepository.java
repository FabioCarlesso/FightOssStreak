package dev.fos.repo;

import dev.fos.model.DisclaimerAcceptance;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisclaimerAcceptanceRepository extends JpaRepository<DisclaimerAcceptance, Long> {

    Optional<DisclaimerAcceptance> findFirstByUserIdAndVersionOrderByAcceptedAtDesc(
            Long userId, String version);

    void deleteByUserId(Long userId);
}
