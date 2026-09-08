package dev.fos.repo;

import dev.fos.model.DrillLog;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrillLogRepository extends JpaRepository<DrillLog, Long> {

    /** Datas distintas com registro — entrada do cálculo de streak. */
    @Query(
            "select distinct d.drilledOn from DrillLog d where d.userId = :userId order by d.drilledOn desc")
    List<LocalDate> findDistinctDrillDates(@Param("userId") Long userId);

    /**
     * Datas distintas de drill <b>avulso</b> — a outra metade do insumo do streak (#114, D58).
     *
     * <p>Drill vinculado fica de fora porque a sessão dele já responde por aquele dia, e somar as
     * duas listas contaria o mesmo dia duas vezes sem mudar resultado nenhum — o cálculo trabalha
     * sobre um conjunto. O que a separação evita de verdade é o outro lado: um dia entrar pelo
     * drill de uma sessão de {@code DESCANSO}, que por definição não é dia de treino. Vincular
     * técnica a descanso é recusado justamente para essa combinação não existir.
     */
    @Query(
            "select distinct d.drilledOn from DrillLog d"
                    + " where d.userId = :userId and d.sessionId is null")
    List<LocalDate> findDistinctStandaloneDrillDates(@Param("userId") Long userId);

    /**
     * Quantos drills <b>avulsos</b> por dia, dentro do período — a outra metade do heatmap (#102).
     *
     * <p>Mesmo recorte de {@link #findDistinctStandaloneDrillDates}: drill vinculado fica de fora
     * porque a sessão dele já responde por aquele dia, e contá-lo aqui acenderia duas vezes o mesmo
     * treino — inclusive o de uma sessão de {@code DESCANSO}, que não é dia de treino.
     */
    @Query(
            "select new dev.fos.repo.DayCount(d.drilledOn, count(d)) from DrillLog d"
                    + " where d.userId = :userId and d.sessionId is null"
                    + " and d.drilledOn between :from and :to"
                    + " group by d.drilledOn")
    List<DayCount> countStandaloneDrillsByDay(
            @Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Drills do período — o que a linha do tempo do diário mostra por dia (#114). */
    List<DrillLog> findByUserIdAndDrilledOnBetweenOrderByDrilledOnDescIdDesc(
            Long userId, LocalDate from, LocalDate to);

    /** Técnicas vinculadas a uma sessão. */
    List<DrillLog> findByUserIdAndSessionIdOrderByIdAsc(Long userId, Long sessionId);

    List<DrillLog> findByUserIdAndSessionIdAndNodeId(Long userId, Long sessionId, Long nodeId);

    /** Todas as técnicas vinculadas às sessões informadas, para montar a linha do tempo. */
    List<DrillLog> findByUserIdAndSessionIdInOrderByIdAsc(Long userId, Collection<Long> sessionIds);

    boolean existsByUserIdAndSessionId(Long userId, Long sessionId);

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
