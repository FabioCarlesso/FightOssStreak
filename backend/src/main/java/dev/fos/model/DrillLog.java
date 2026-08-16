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
import java.time.LocalDate;

/**
 * Registro de "treinei isso hoje".
 *
 * <p>É a única entrada de dado que alimenta simultaneamente o streak e a agenda de SRS. Fica
 * como log append-only — o histórico é o que permite reavaliar depois se a mecânica funcionou
 * (critério de falha em docs/05-mvp-web-plano.md).
 */
@Entity
@Table(name = "drill_log")
public class DrillLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "drilled_on", nullable = false)
    private LocalDate drilledOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recall recall;

    @Column
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DrillLog() {
        // JPA
    }

    public DrillLog(
            Long userId, Long nodeId, LocalDate drilledOn, Recall recall, String note, Instant createdAt) {
        this.userId = userId;
        this.nodeId = nodeId;
        this.drilledOn = drilledOn;
        this.recall = recall;
        this.note = note;
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

    public LocalDate getDrilledOn() {
        return drilledOn;
    }

    public Recall getRecall() {
        return recall;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
