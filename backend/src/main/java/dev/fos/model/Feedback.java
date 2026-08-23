package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Feedback de um usuário: bug, conteúdo errado, troca de vídeo ou sugestão
 * (docs/13-feedback-usuarios.md).
 *
 * <p>{@code nodeId} é opcional — nem todo feedback é sobre um nó do currículo.
 */
@Entity
@Table(name = "feedback")
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "node_id")
    private Long nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackCategory category;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    protected Feedback() {
        // JPA
    }

    public Feedback(
            Long userId,
            Long nodeId,
            FeedbackCategory category,
            String message,
            Instant createdAt) {
        this.userId = userId;
        this.nodeId = nodeId;
        this.category = category;
        this.message = message;
        this.status = FeedbackStatus.ABERTO;
        this.createdAt = createdAt;
    }

    public void decide(FeedbackStatus status, Long decidedBy, Instant now) {
        this.status = status;
        this.decidedBy = decidedBy;
        this.decidedAt = now;
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

    public FeedbackCategory getCategory() {
        return category;
    }

    public String getMessage() {
        return message;
    }

    public FeedbackStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }
}
