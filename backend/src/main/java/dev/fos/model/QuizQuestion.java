package dev.fos.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Pergunta conceitual de um nó.
 *
 * <p>Testa compreensão — <em>quando usar</em>, <em>qual o erro comum</em>, <em>o que o oponente
 * faz</em> — e nunca decoreba de sequência de passos.
 */
@Entity
@Table(name = "quiz_question")
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    @Column(nullable = false)
    private String prompt;

    @Column(nullable = false)
    private String explanation;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<QuizOption> options = new ArrayList<>();

    protected QuizQuestion() {
        // JPA
    }

    public QuizQuestion(Node node, String prompt, String explanation, int orderIndex) {
        this.node = node;
        this.prompt = prompt;
        this.explanation = explanation;
        this.orderIndex = orderIndex;
    }

    public void addOption(QuizOption option) {
        options.add(option);
    }

    public Long getId() {
        return id;
    }

    public Node getNode() {
        return node;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getExplanation() {
        return explanation;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public List<QuizOption> getOptions() {
        return options;
    }

    public QuizOption correctOption() {
        return options.stream()
                .filter(QuizOption::isCorrect)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Pergunta sem alternativa correta: " + id));
    }
}
