package dev.fos.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

/**
 * Um requisito do próximo grau, colado pela própria pessoa da lista que a academia dela entregou
 * (#196, D78).
 *
 * <p>O status <b>não é gravado</b>: requisito ligado a nós é derivado do progresso deles a cada
 * leitura, como o streak e a contagem de aulas. Só o requisito sem nó (comportamento, tempo de
 * faixa) tem marcação à mão, porque não há nada no app de onde derivá-lo.
 */
@Entity
@Table(name = "graduation_requirement")
public class GraduationRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(nullable = false)
    private String text;

    @Column(name = "manual_done", nullable = false)
    private boolean manualDone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Códigos dos nós ligados — a chave estável do currículo (D11). Em lote: a tela lê todos. */
    @BatchSize(size = 64)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "graduation_requirement_node",
            joinColumns = @JoinColumn(name = "requirement_id"))
    @Column(name = "node_code", nullable = false)
    private Set<String> nodeCodes = new LinkedHashSet<>();

    protected GraduationRequirement() {
        // JPA
    }

    public GraduationRequirement(Long userId, int orderIndex, String text, Instant createdAt) {
        this.userId = userId;
        this.orderIndex = orderIndex;
        this.text = text;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isManualDone() {
        return manualDone;
    }

    public void setManualDone(boolean manualDone) {
        this.manualDone = manualDone;
    }

    public Set<String> getNodeCodes() {
        return nodeCodes;
    }

    /** Substitui o conteúdo, e não a referência: a coleção é gerenciada pelo JPA. */
    public void setNodeCodes(Collection<String> codes) {
        this.nodeCodes.clear();
        this.nodeCodes.addAll(codes);
    }
}
