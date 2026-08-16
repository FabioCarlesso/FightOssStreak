package dev.fos.repo;

import dev.fos.model.QuizAttempt;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /** Tentativas dentro da janela de medição, para contar nós revisitados. */
    List<QuizAttempt> findByUserIdAndAttemptedOnGreaterThanEqual(Long userId, LocalDate from);
}
