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
 * Uma submissão de quiz.
 *
 * <p>Log append-only, deliberadamente separado de {@link UserProgress}: o progresso guarda o estado
 * atual (última nota, conclusão) e o histórico guarda o que aconteceu. Sem ele não há como
 * distinguir um quiz respondido uma vez de um quiz refeito — e "quiz refeito espontaneamente" é um
 * dos critérios de sucesso do MVP (docs/05-mvp-web-plano.md), justamente por ser sinal de retenção
 * real em vez de streak vazio.
 */
@Entity
@Table(name = "quiz_attempt")
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "attempted_on", nullable = false)
    private LocalDate attemptedOn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QuizAttempt() {
        // JPA
    }

    public QuizAttempt(
            Long userId, Long nodeId, int score, boolean passed, LocalDate attemptedOn, Instant createdAt) {
        this.userId = userId;
        this.nodeId = nodeId;
        this.score = score;
        this.passed = passed;
        this.attemptedOn = attemptedOn;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getNodeId() {
        return nodeId;
    }

    public int getScore() {
        return score;
    }

    public boolean isPassed() {
        return passed;
    }

    public LocalDate getAttemptedOn() {
        return attemptedOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
