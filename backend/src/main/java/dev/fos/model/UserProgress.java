package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Progresso persistido de um nó.
 *
 * <p>{@link ProgressStatus#LOCKED} nunca é gravado aqui: bloqueio é derivado do grafo em tempo de
 * leitura. Persistir bloqueio significaria ter de reescrever linhas sempre que o currículo mudasse.
 */
@Entity
@Table(name = "user_progress")
public class UserProgress {

    @EmbeddedId private UserNodeKey id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProgressStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_quiz_score")
    private Integer lastQuizScore;

    /**
     * Anotação fixada do usuário sobre este nó — o detalhe que ele quer reler toda vez que o nó
     * abre.
     *
     * <p>Não se confunde com {@code drill_log.note}, que é o que aconteceu em um treino específico
     * e fica no histórico. {@code null} é o estado "sem anotação"; nota em branco limpa a coluna em
     * vez de gravar string vazia, para que esse estado tenha uma representação só.
     */
    @Column(name = "pinned_note")
    private String pinnedNote;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserProgress() {
        // JPA
    }

    public UserProgress(UserNodeKey id, ProgressStatus status, Instant updatedAt) {
        this.id = id;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public UserNodeKey getId() {
        return id;
    }

    public ProgressStatus getStatus() {
        return status;
    }

    public void setStatus(ProgressStatus status) {
        this.status = status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Integer getLastQuizScore() {
        return lastQuizScore;
    }

    public void setLastQuizScore(Integer lastQuizScore) {
        this.lastQuizScore = lastQuizScore;
    }

    public String getPinnedNote() {
        return pinnedNote;
    }

    public void setPinnedNote(String pinnedNote) {
        this.pinnedNote = pinnedNote;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
