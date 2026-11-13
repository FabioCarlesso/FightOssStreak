package dev.fos.repo;

import dev.fos.model.GraduationRequirement;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Requisitos do próximo grau (#196, D78). Toda leitura é recortada pela conta. */
public interface GraduationRequirementRepository
        extends JpaRepository<GraduationRequirement, Long> {

    List<GraduationRequirement> findByUserIdOrderByOrderIndexAscIdAsc(Long userId);

    /** Pela conta junto: requisito de outra conta é "não encontrado", e não "proibido". */
    Optional<GraduationRequirement> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Derivado, e não JPQL: carrega e apaga um a um para a tabela de vínculos ir junto. */
    void deleteByUserId(Long userId);
}
