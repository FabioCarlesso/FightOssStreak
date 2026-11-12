package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A meta do próximo grau de uma conta (#193, D75).
 *
 * <p>Os números vêm <b>de fora</b>: são o mínimo de aulas que a academia de quem usa exige,
 * digitados pela própria pessoa. O app não traz lista nem mínimo de academia nenhuma — material de
 * academia costuma ser protegido, e o repositório é público.
 *
 * <p>Uma linha por conta, com a conta como chave: o FOS acompanha o <i>próximo</i> grau, e não um
 * histórico de graduações. Trocar de grau é reescrever a linha com a data nova.
 */
@Entity
@Table(name = "graduation_goal")
public class GraduationGoal {

    @Id
    @Column(name = "user_id")
    private Long userId;

    /** Começo da contagem, inclusivo: a aula do dia da graduação já conta para o próximo grau. */
    @Column(name = "last_graduation_on", nullable = false)
    private LocalDate lastGraduationOn;

    @Column(name = "current_label")
    private String currentLabel;

    @Column(name = "next_label")
    private String nextLabel;

    @Column(name = "min_total")
    private Integer minTotal;

    @Column(name = "min_gi")
    private Integer minGi;

    @Column(name = "min_nogi")
    private Integer minNogi;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GraduationGoal() {
        // JPA
    }

    public GraduationGoal(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getLastGraduationOn() {
        return lastGraduationOn;
    }

    public void setLastGraduationOn(LocalDate lastGraduationOn) {
        this.lastGraduationOn = lastGraduationOn;
    }

    public String getCurrentLabel() {
        return currentLabel;
    }

    public void setCurrentLabel(String currentLabel) {
        this.currentLabel = currentLabel;
    }

    public String getNextLabel() {
        return nextLabel;
    }

    public void setNextLabel(String nextLabel) {
        this.nextLabel = nextLabel;
    }

    public Integer getMinTotal() {
        return minTotal;
    }

    public void setMinTotal(Integer minTotal) {
        this.minTotal = minTotal;
    }

    public Integer getMinGi() {
        return minGi;
    }

    public void setMinGi(Integer minGi) {
        this.minGi = minGi;
    }

    public Integer getMinNogi() {
        return minNogi;
    }

    public void setMinNogi(Integer minNogi) {
        this.minNogi = minNogi;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch(Instant quando) {
        this.updatedAt = quando;
    }
}
