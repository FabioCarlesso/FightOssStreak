package dev.fos.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * Uma posição do mapa de posições (#194, D76): guarda fechada, montada, costas...
 *
 * <p>Dado do currículo (D11): vem de {@code curriculum/positions.json} e é reescrita a cada
 * ingestão. Nenhum dado de usuário aponta para ela — o nó guarda o <b>código</b> da posição, e é o
 * {@code CurriculumValidator} que garante que o código existe.
 *
 * <p>{@code identify} diz <b>como reconhecer</b> a posição, nunca como chegar nela nem o que fazer
 * dali: identificar sim, ensinar não (D1).
 */
@Entity
@Table(name = "curriculum_position")
public class CurriculumPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionFamily family;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PositionPerspective perspective;

    @Column(nullable = false)
    private String identify;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    /** Na ordem do JSON: o primeiro apelido é o que a tela mostra primeiro. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "curriculum_position_alias",
            joinColumns = @JoinColumn(name = "position_id"))
    @OrderColumn(name = "alias_order")
    @Column(name = "alias", nullable = false)
    private List<String> aliases = new ArrayList<>();

    protected CurriculumPosition() {
        // JPA
    }

    public CurriculumPosition(String code) {
        this.code = code;
    }

    /** Usado pela ingestão, que reescreve a posição a partir do JSON versionado. */
    public void update(
            String name,
            PositionFamily family,
            PositionPerspective perspective,
            String identify,
            int orderIndex,
            List<String> aliases) {
        this.name = name;
        this.family = family;
        this.perspective = perspective;
        this.identify = identify;
        this.orderIndex = orderIndex;
        this.aliases.clear();
        this.aliases.addAll(aliases);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public PositionFamily getFamily() {
        return family;
    }

    public PositionPerspective getPerspective() {
        return perspective;
    }

    public String getIdentify() {
        return identify;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public List<String> getAliases() {
        return aliases;
    }
}
