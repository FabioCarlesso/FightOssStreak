package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Um dia sem treino que foi perdoado (#99, D55).
 *
 * <p>Não é cache do cálculo — o streak segue derivado do {@code drill_log} a cada leitura. É o
 * livro-caixa do saldo: um freeze gasto continua gasto depois que a corrente que ele salvava
 * quebrou. Sem isso o saldo seria "por corrente" e não "por mês", e bastaria deixar a sequência
 * morrer para ganhar freeze novo.
 */
@Entity
@Table(name = "streak_freeze")
public class StreakFreeze {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** O dia sem treino coberto. Nunca é hoje: o dia ainda não acabou. */
    @Column(name = "covered_on", nullable = false)
    private LocalDate coveredOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StreakFreeze() {
        // JPA
    }

    public StreakFreeze(Long userId, LocalDate coveredOn, Instant createdAt) {
        this.userId = userId;
        this.coveredOn = coveredOn;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getCoveredOn() {
        return coveredOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
