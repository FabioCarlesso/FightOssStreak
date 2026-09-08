package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Um treino inteiro (#114, D56): o que aconteceu no tatame naquele dia.
 *
 * <p>É a <b>entrada</b> do app, e o currículo é a saída. Técnica vinculada continua sendo {@link
 * DrillLog} — com {@code session_id} preenchido —, e não uma tabela paralela: um segundo registro
 * de "treinei isto" daria ao SRS duas verdades sobre o mesmo fato.
 *
 * <p>Só {@code trainedOn} e {@code kind} são obrigatórios, e isso é desenho de produto e não
 * frouxidão de schema: seis campos no vestiário é o jeito de garantir que ninguém preencha. Sessão
 * incompleta é sessão válida, e não existe estado de rascunho — "treinei" agora, completa depois.
 *
 * <p>{@code weightKg} e {@code feeling} são <b>dado referente à saúde</b> (LGPD, art. 5º, II):
 * ficam só na conta de quem escreveu, {@code DELETE /api/me} os leva junto, e a coleta da D50 e o
 * painel da D52 nunca os veem (docs/11-privacidade.md).
 */
@Entity
@Table(name = "training_session")
public class TrainingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "trained_on", nullable = false)
    private LocalDate trainedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionKind kind;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column
    private Feeling feeling;

    /** {@code NUMERIC(5,2)} e não ponto flutuante: peso é dinheiro, não medida aproximada. */
    @Column(name = "weight_kg")
    private BigDecimal weightKg;

    @Column private String learned;

    @Column private String improve;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TrainingSession() {
        // JPA
    }

    public TrainingSession(Long userId, LocalDate trainedOn, SessionKind kind, Instant createdAt) {
        this.userId = userId;
        this.trainedOn = trainedOn;
        this.kind = kind;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getTrainedOn() {
        return trainedOn;
    }

    public void setTrainedOn(LocalDate trainedOn) {
        this.trainedOn = trainedOn;
    }

    public SessionKind getKind() {
        return kind;
    }

    public void setKind(SessionKind kind) {
        this.kind = kind;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public Feeling getFeeling() {
        return feeling;
    }

    public void setFeeling(Feeling feeling) {
        this.feeling = feeling;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getLearned() {
        return learned;
    }

    public void setLearned(String learned) {
        this.learned = learned;
    }

    public String getImprove() {
        return improve;
    }

    public void setImprove(String improve) {
        this.improve = improve;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch(Instant quando) {
        this.updatedAt = quando;
    }
}
