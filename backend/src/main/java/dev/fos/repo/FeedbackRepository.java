package dev.fos.repo;

import dev.fos.model.Feedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /** Fila do dono, da mais antiga para a mais nova — mesmo critério da fila de acesso. */
    List<Feedback> findAllByOrderByCreatedAtAsc();
}
