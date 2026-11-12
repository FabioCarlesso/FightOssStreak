package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A checklist de requisitos do próximo grau de uma conta (#196, D78): só o rótulo do grupo. Os
 * itens são {@link GraduationRequirement}.
 *
 * <p>Uma por conta, com a conta como chave, pelo mesmo motivo da {@link GraduationGoal}: o FOS
 * acompanha o próximo grau, e não um histórico de graduações.
 */
@Entity
@Table(name = "graduation_checklist")
public class GraduationChecklist {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column private String label;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GraduationChecklist() {
        // JPA
    }

    public GraduationChecklist(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch(Instant quando) {
        this.updatedAt = quando;
    }
}
