package dev.fos.repo;

import dev.fos.model.AppStart;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface AppStartRepository extends JpaRepository<AppStart, Long> {

    /** As últimas subidas, da mais nova para a mais antiga. */
    List<AppStart> findByOrderByStartedAtDesc(Pageable pageable);

    /**
     * Quantas subidas houve na janela.
     *
     * <p>É o número que separa "o app está de pé há semanas" de "ele reinicia todo dia às 3h" —
     * duas situações que a lista das últimas subidas mostra igual quando a janela é curta.
     */
    long countByStartedAtGreaterThanEqual(Instant from);

    /** Transação própria pelo mesmo motivo do expurgo das estatísticas. */
    @Modifying
    @Transactional
    @Query("delete from AppStart s where s.startedAt < :limite")
    int deleteByStartedAtBefore(Instant limite);
}
