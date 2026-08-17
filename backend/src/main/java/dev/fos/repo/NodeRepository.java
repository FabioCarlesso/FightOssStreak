package dev.fos.repo;

import dev.fos.model.Node;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NodeRepository extends JpaRepository<Node, Long> {

    Optional<Node> findByCode(String code);

    /**
     * Ordenado por módulo e depois por posição dentro do módulo — a ordem de exibição da árvore.
     */
    @Query("select n from Node n join fetch n.module m order by m.orderIndex asc, n.orderIndex asc")
    List<Node> findAllOrdered();
}
