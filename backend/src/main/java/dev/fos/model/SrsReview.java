package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Estado do agendamento SM-2 de um nó para um usuário. */
@Entity
@Table(name = "srs_review")
public class SrsReview {

    @EmbeddedId
    private UserNodeKey id;

    @Column(name = "next_review_on", nullable = false)
    private LocalDate nextReviewOn;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "ease_factor", nullable = false)
    private double easeFactor;

    @Column(nullable = false)
    private int repetitions;

    @Column(name = "last_reviewed_on")
    private LocalDate lastReviewedOn;

    protected SrsReview() {
        // JPA
    }

    public SrsReview(
            UserNodeKey id,
            LocalDate nextReviewOn,
            int intervalDays,
            double easeFactor,
            int repetitions) {
        this.id = id;
        this.nextReviewOn = nextReviewOn;
        this.intervalDays = intervalDays;
        this.easeFactor = easeFactor;
        this.repetitions = repetitions;
    }

    public UserNodeKey getId() {
        return id;
    }

    public LocalDate getNextReviewOn() {
        return nextReviewOn;
    }

    public void setNextReviewOn(LocalDate nextReviewOn) {
        this.nextReviewOn = nextReviewOn;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public double getEaseFactor() {
        return easeFactor;
    }

    public void setEaseFactor(double easeFactor) {
        this.easeFactor = easeFactor;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public void setRepetitions(int repetitions) {
        this.repetitions = repetitions;
    }

    public LocalDate getLastReviewedOn() {
        return lastReviewedOn;
    }

    public void setLastReviewedOn(LocalDate lastReviewedOn) {
        this.lastReviewedOn = lastReviewedOn;
    }
}
