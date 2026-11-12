package dev.fos.repo;

import dev.fos.model.CurriculumPosition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** O mapa de posições (#194, D76). */
public interface CurriculumPositionRepository extends JpaRepository<CurriculumPosition, Long> {

    List<CurriculumPosition> findAllByOrderByOrderIndexAsc();
}
