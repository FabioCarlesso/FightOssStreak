package dev.fos.repo;

import dev.fos.model.QuizQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    @Query(
            """
            select distinct q from QuizQuestion q
            left join fetch q.options
            where q.node.id = :nodeId
            order by q.orderIndex asc
            """)
    List<QuizQuestion> findByNodeIdWithOptions(Long nodeId);

    List<QuizQuestion> findByNodeIdOrderByOrderIndexAsc(Long nodeId);
}
