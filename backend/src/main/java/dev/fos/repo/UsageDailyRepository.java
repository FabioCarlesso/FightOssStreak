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
     * As contagens de uma faixa de dias — a única leitura que o painel (#85) faz.
     *
     * <p>Uma consulta para o período pedido <b>e</b> o anterior de mesmo tamanho, e não duas: o
     * comparativo precisa dos dois de qualquer forma, e a faixa inteira de 90 + 90 dias são poucas
     * centenas de linhas nesta escala. Repare que o painel nunca chega em {@code usage_event}: o
     * cru tem chave de visita e às vezes {@code user_id}, e o painel é agregado e de ninguém.
     */
    List<UsageDaily> findByOccurredOnBetween(LocalDate from, LocalDate to);

    /**
     * O dia mais recente que já tem contagem — {@code null} quando a agregação nunca rodou.
     *
     * <p>O painel mostra este valor porque a diferença entre "ninguém acessou" e "o job ainda não
     * fechou o dia" não é visível no número: as duas dão zero.
     */
    @Query("select max(d.occurredOn) from UsageDaily d")
    LocalDate ultimoDiaAgregado();

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
