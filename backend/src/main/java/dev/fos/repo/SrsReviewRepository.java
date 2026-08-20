package dev.fos.repo;

import dev.fos.model.SrsReview;
import dev.fos.model.UserNodeKey;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SrsReviewRepository extends JpaRepository<SrsReview, UserNodeKey> {

    List<SrsReview> findByIdUserId(Long userId);

    /** Nós vencidos ou vencendo hoje — a agenda de "o que drillar". */
    List<SrsReview> findByIdUserIdAndNextReviewOnLessThanEqual(Long userId, LocalDate onOrBefore);

    void deleteByIdUserId(Long userId);
}
