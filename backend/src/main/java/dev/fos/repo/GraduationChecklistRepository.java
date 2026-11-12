package dev.fos.repo;

import dev.fos.model.GraduationChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

/** A checklist do próximo grau (#196, D78). A chave é a própria conta. */
public interface GraduationChecklistRepository extends JpaRepository<GraduationChecklist, Long> {}
