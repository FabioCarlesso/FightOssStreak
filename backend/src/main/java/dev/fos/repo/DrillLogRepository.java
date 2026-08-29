package dev.fos.repo;

import dev.fos.model.DrillLog;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrillLogRepository extends JpaRepository<DrillLog, Long> {

    /** Datas distintas com registro — entrada do cálculo de streak. */
    @Query(
            "select distinct d.drilledOn from DrillLog d where d.userId = :userId order by d.drilledOn desc")
    List<LocalDate> findDistinctDrillDates(@Param("userId") Long userId);

    List<DrillLog> findByUserIdAndNodeIdOrderByDrilledOnDesc(Long userId, Long nodeId);

    /** Registros dentro da janela de medição dos critérios de sucesso do MVP. */
    List<DrillLog> findByUserIdAndDrilledOnGreaterThanEqual(Long userId, LocalDate from);

    /** Tudo que a conta registrou — usado pela cópia da conta-modelo (#62). */
    List<DrillLog> findByUserId(Long userId);

    long countByUserId(Long userId);

    /**
     * Quantas contas registraram drill no período — o "ativas" do painel (#85).
     *
     * <p>Sai daqui, e não da coleta de uso, porque o painel não toca a tabela crua de eventos: é lá
     * que existe {@code user_id}, e a promessa da D50 é que o painel seja agregado e de ninguém. O
     * drill é registro de progresso da própria conta, que ela já vê na tela dela, e aqui só o
     * <em>número</em> de contas distintas sai — nenhum id atravessa este método.
     */
    @Query(
            "select count(distinct d.userId) from DrillLog d"
                    + " where d.drilledOn between :from and :to")
    long countDistinctUsersBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    void deleteByUserId(Long userId);
}
