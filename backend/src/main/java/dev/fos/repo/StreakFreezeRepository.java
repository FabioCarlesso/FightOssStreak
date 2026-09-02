package dev.fos.repo;

import dev.fos.model.StreakFreeze;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Grava um dia perdoado, ignorando o que já estiver lá.
     *
     * <p>Nativo com {@code ON CONFLICT DO NOTHING}, e não {@code save()}, porque {@code GET
     * /api/streak} <b>escreve</b> e duas requisições da mesma conta chegam juntas o tempo todo —
     * duas abas abertas bastam. Com {@code save()} as duas derivam o mesmo dia perdido, as duas
     * tentam inserir, e a que perde a corrida morre na {@code uq_streak_freeze} com 500: o estado
     * final ficava certo (uma linha) e a pessoa via erro na home. Pior, o 5xx era da própria
     * aplicação e entrava na taxa que dispara o alerta de incidente da D54 — duas abas abertas
     * podiam mandar e-mail de "site fora do ar".
     *
     * <p>A restrição única precisa ser <b>rede</b>, e não mina: quem perde a corrida não tem nada a
     * fazer, porque o que ela queria gravar já está gravado. É também o que torna a gravação segura
     * de repetir em toda leitura do streak, que é o desenho da D55.
     *
     * <p>É {@code WHERE NOT EXISTS} e não {@code ON CONFLICT DO NOTHING} por portabilidade: o H2
     * não parseia o segundo nem no modo de compatibilidade PostgreSQL, e a regra deste repositório
     * é a das migrations — o mesmo SQL roda nos dois bancos. A diferença prática é que o {@code ON
     * CONFLICT} seria atômico no servidor, enquanto aqui sobra a janela entre a subconsulta e a
     * inserção: microssegundos dentro de <b>uma</b> instrução, contra a janela anterior, que cobria
     * o cálculo inteiro do streak. Não é zero. Se um dia aparecer violação desta restrição no log,
     * o conserto é {@code ON CONFLICT DO NOTHING} e trocar o banco de teste, não mais uma volta
     * nesta consulta.
     */
    @Modifying
    @Query(
            value =
                    "insert into streak_freeze (user_id, covered_on, created_at)"
                            + " select :userId, :coveredOn, :createdAt"
                            + " where not exists (select 1 from streak_freeze f"
                            + " where f.user_id = :userId and f.covered_on = :coveredOn)",
            nativeQuery = true)
    void inserirSeAusente(
            @Param("userId") Long userId,
            @Param("coveredOn") LocalDate coveredOn,
            @Param("createdAt") Instant createdAt);

    /** Devolve ao saldo os dias que deixaram de ser dia perdido (ganharam registro depois). */
    void deleteByUserIdAndCoveredOnIn(Long userId, Collection<LocalDate> coveredOn);

    void deleteByUserId(Long userId);
}
