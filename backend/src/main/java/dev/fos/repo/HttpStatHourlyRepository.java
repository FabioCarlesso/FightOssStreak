package dev.fos.repo;

import dev.fos.model.HttpStatHourly;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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

    /** O expurgo: a tabela é pequena, mas hora vezes rota cresce sozinho para sempre. */
    @Modifying
    @Query("delete from HttpStatHourly s where s.hourStart < :limite")
    int deleteByHourStartBefore(Instant limite);
}
