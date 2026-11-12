package dev.fos.repo;

import dev.fos.model.GraduationGoal;
import org.springframework.data.jpa.repository.JpaRepository;

/** A meta do próximo grau (#193, D75). A chave é a própria conta. */
public interface GraduationGoalRepository extends JpaRepository<GraduationGoal, Long> {}
