package dev.fos.repo;

import dev.fos.model.SessionKind;
import dev.fos.model.TrainingSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {

    /**
     * Datas distintas com sessão que conta como treino — metade do insumo do streak (D58).
     *
     * <p>O filtro por tipo mora na consulta, e não em quem chama, porque ele é a regra: {@code
     * DESCANSO} é dia anotado <b>sem</b> treino, e deixá-lo passar transformaria a corrente em
     * "abri o app". A outra metade são os drills avulsos, que o {@link DrillLogRepository} já sabe
     * listar.
     */
    @Query(
            "select distinct s.trainedOn from TrainingSession s"
                    + " where s.userId = :userId and s.kind <> dev.fos.model.SessionKind.DESCANSO")
    List<LocalDate> findDistinctTrainingDates(@Param("userId") Long userId);

    /** Linha do tempo do diário, do mais recente para o mais antigo. */
    List<TrainingSession> findByUserIdAndTrainedOnBetweenOrderByTrainedOnDescIdDesc(
            Long userId, LocalDate from, LocalDate to);

    /**
     * A sessão, se ela for desta conta.
     *
     * <p>O {@code userId} entra na consulta e não numa conferência depois: 404 para sessão de outra
     * pessoa é a mesma resposta que para sessão inexistente, e é assim que se evita que a rota vire
     * consulta de quais ids existem.
     */
    Optional<TrainingSession> findByIdAndUserId(Long id, Long userId);

    /**
     * Quantos <b>treinos</b> no período — {@code DESCANSO} fora, pela mesma regra da consulta
     * acima.
     *
     * <p>O filtro não é detalhe de contagem: este número é o que a home e o diário mostram como
     * "treinos registrados neste mês", e contar descanso ali diria "treino" na mesma tela em que o
     * dia aparece marcado como <i>não conta no streak</i> (D58).
     */
    @Query(
            "select count(s) from TrainingSession s"
                    + " where s.userId = :userId"
                    + " and s.kind <> dev.fos.model.SessionKind.DESCANSO"
                    + " and s.trainedOn between :from and :to")
    long countTrainingSessions(
            @Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByUserIdAndKind(Long userId, SessionKind kind);

    void deleteByUserId(Long userId);
}
