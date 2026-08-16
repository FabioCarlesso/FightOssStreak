package dev.fos.repo;

import dev.fos.model.CurriculumModule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModuleRepository extends JpaRepository<CurriculumModule, Long> {

    Optional<CurriculumModule> findByCode(String code);

    List<CurriculumModule> findAllByOrderByOrderIndexAsc();
}
