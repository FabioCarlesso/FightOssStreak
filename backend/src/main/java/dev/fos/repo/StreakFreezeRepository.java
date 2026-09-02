package dev.fos.repo;

import dev.fos.model.StreakFreeze;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreakFreezeRepository extends JpaRepository<StreakFreeze, Long> {

    /**
     * Dias já perdoados da conta — entrada do cálculo de streak com freeze.
     *
     * <p>Sem recorte de data: o universo é de no máximo {@code freezes-por-mês} linhas por mês de
     * uso, e o cálculo precisa tanto do saldo do mês corrente quanto dos dias que sustentam a
     * corrente atual, que pode atravessar meses.
     */
    @Query("select f.coveredOn from StreakFreeze f where f.userId = :userId")
    List<LocalDate> findCoveredDates(@Param("userId") Long userId);

    /** Devolve ao saldo os dias que deixaram de ser dia perdido (ganharam registro depois). */
    void deleteByUserIdAndCoveredOnIn(Long userId, Collection<LocalDate> coveredOn);

    void deleteByUserId(Long userId);
}
