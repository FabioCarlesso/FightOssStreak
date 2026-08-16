package dev.fos.repo;

import dev.fos.model.DrillLog;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DrillLogRepository extends JpaRepository<DrillLog, Long> {

    /** Datas distintas com registro — entrada do cálculo de streak. */
    @Query("select distinct d.drilledOn from DrillLog d where d.userId = :userId order by d.drilledOn desc")
    List<LocalDate> findDistinctDrillDates(@Param("userId") Long userId);

    List<DrillLog> findByUserIdAndNodeIdOrderByDrilledOnDesc(Long userId, Long nodeId);

    long countByUserId(Long userId);
}
