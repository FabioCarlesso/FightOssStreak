package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_option")
public class QuizOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    protected QuizOption() {
        // JPA
    }

    public QuizOption(QuizQuestion question, String label, boolean correct, int orderIndex) {
        this.question = question;
        this.label = label;
        this.correct = correct;
        this.orderIndex = orderIndex;
    }

    public Long getId() {
        return id;
    }

    public QuizQuestion getQuestion() {
        return question;
    }

    public String getLabel() {
        return label;
    }

    public boolean isCorrect() {
        return correct;
    }

    public int getOrderIndex() {
        return orderIndex;
    }
}
