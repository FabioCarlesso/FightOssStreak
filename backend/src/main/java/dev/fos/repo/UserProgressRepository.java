package dev.fos.repo;

import dev.fos.model.UserNodeKey;
import dev.fos.model.UserProgress;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProgressRepository extends JpaRepository<UserProgress, UserNodeKey> {

    List<UserProgress> findByIdUserId(Long userId);
}
