package dev.fos.repo;

import dev.fos.model.HttpStatHourly;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface HttpStatHourlyRepository extends JpaRepository<HttpStatHourly, Long> {

    /**
     * A linha de uma (hora, rota), quando ela já existe.
     *
     * <p>O flush roda várias vezes dentro da mesma hora, então encontrar a linha e somar é o caso
     * normal — não a exceção.
     */
    Optional<HttpStatHourly> findByHourStartAndPath(Instant hourStart, String path);

    /** A janela que o painel lê: da hora inicial (inclusive) até agora. */
    List<HttpStatHourly> findByHourStartGreaterThanEqualOrderByHourStartAsc(Instant from);

    /**
     * O expurgo: a tabela é pequena, mas hora vezes rota cresce sozinho para sempre.
     *
     * <p>A transação é <b>deste método</b>, e não do job que o chama, de propósito: o job engole a
     * falha para não morrer calado no agendador, e um {@code try/catch} <em>dentro</em> de um
     * método transacional deixaria a transação marcada para rollback e estouraria {@code
     * UnexpectedRollbackException} no commit — fora do {@code catch}, que é o pior dos dois mundos.
     * Mesmo desenho do {@code UsageMaintenanceJob}, que também captura de fora.
     */
    @Modifying
    @Transactional
    @Query("delete from HttpStatHourly s where s.hourStart < :limite")
    int deleteByHourStartBefore(Instant limite);
}
