package dev.fos.repo;

import dev.fos.model.UsageDaily;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UsageDailyRepository extends JpaRepository<UsageDaily, Long> {

    List<UsageDaily> findByOccurredOn(LocalDate occurredOn);

    boolean existsByOccurredOn(LocalDate occurredOn);

    /**
     * Reagregar um dia começa apagando o que havia dele: o job precisa ser idempotente.
     *
     * <p>{@code delete} em JPQL, e não o derivado que carrega e remove entidade por entidade: o
     * derivado só sai no flush, e o Hibernate ordena INSERT antes de DELETE — as linhas novas do
     * mesmo dia batiam na única do índice contra as antigas, que ainda não tinham saído.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UsageDaily d where d.occurredOn = :occurredOn")
    void deleteByOccurredOn(LocalDate occurredOn);
}
