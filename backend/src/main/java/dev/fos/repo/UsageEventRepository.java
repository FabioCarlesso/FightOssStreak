package dev.fos.repo;

import dev.fos.model.UsageEvent;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UsageEventRepository extends JpaRepository<UsageEvent, Long> {

    /** Os dias que têm linha crua, do mais antigo para o mais novo — a pauta da agregação. */
    @Query(
            "select distinct e.occurredOn from UsageEvent e where e.occurredOn < :limite"
                    + " order by e.occurredOn asc")
    List<LocalDate> diasCrusAntesDe(LocalDate limite);

    List<UsageEvent> findByOccurredOn(LocalDate occurredOn);

    /**
     * O expurgo dos 90 dias. Devolve quantas linhas saíram, que é o que vai para o log.
     *
     * <p>{@code flush} e {@code clear} porque um {@code delete} em JPQL passa por cima do contexto
     * de persistência: sem eles, uma leitura na mesma transação devolveria as linhas que acabaram
     * de ser apagadas — que é exatamente o que o teste de expurgo faz.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UsageEvent e where e.occurredOn < :limite")
    int deleteByOccurredOnBefore(LocalDate limite);

    /**
     * O que {@code DELETE /api/me} apaga (#84, D50).
     *
     * <p>Só o cru: o agregado não guarda id de conta nenhuma, e apagá-lo faria a exclusão de uma
     * conta reescrever o histórico de uso de todo mundo.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UsageEvent e where e.userId = :userId")
    int deleteByUserId(Long userId);

    long countByUserId(Long userId);
}
