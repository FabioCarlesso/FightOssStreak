package dev.fos.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Uma unidade de revisão do currículo.
 *
 * <p>Os pré-requisitos são arestas de um grafo dirigido — um nó tem múltiplos pré-requisitos e
 * múltiplos sucessores. Ciclos são detectados na ingestão, nunca em runtime.
 */
@Entity
@Table(name = "node")
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "module_id", nullable = false)
    private CurriculumModule module;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Belt belt;

    @Column(nullable = false)
    private String concept;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "unlock_rule", nullable = false)
    private UnlockRule unlockRule = UnlockRule.ALL;

    @Embedded private VideoRef video;

    /**
     * Complementares, na ordem em que o JSON os declara — a ordem é curatorial, então {@link
     * OrderColumn} não é detalhe: sem ela a lista volta do banco em ordem arbitrária e o clipe que
     * o curador pôs em primeiro deixa de ser o primeiro da tira.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "node_extra_video", joinColumns = @JoinColumn(name = "node_id"))
    @OrderColumn(name = "position")
    private List<ExtraVideoRef> extraVideos = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "node_prereq", joinColumns = @JoinColumn(name = "node_id"))
    @Column(name = "prereq_node_id", nullable = false)
    private Set<Long> prereqNodeIds = new LinkedHashSet<>();

    protected Node() {
        // JPA
    }

    public Node(
            CurriculumModule module,
            String code,
            String title,
            Belt belt,
            String concept,
            int orderIndex,
            UnlockRule unlockRule,
            VideoRef video,
            List<ExtraVideoRef> extraVideos) {
        this.module = module;
        this.code = code;
        this.title = title;
        this.belt = belt;
        this.concept = concept;
        this.orderIndex = orderIndex;
        this.unlockRule = unlockRule;
        this.video = video;
        setExtraVideos(extraVideos);
    }

    public Long getId() {
        return id;
    }

    public CurriculumModule getModule() {
        return module;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public Belt getBelt() {
        return belt;
    }

    public String getConcept() {
        return concept;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public UnlockRule getUnlockRule() {
        return unlockRule;
    }

    public VideoRef getVideo() {
        return video;
    }

    public List<ExtraVideoRef> getExtraVideos() {
        return extraVideos;
    }

    public Set<Long> getPrereqNodeIds() {
        return prereqNodeIds;
    }

    public void setPrereqNodeIds(Set<Long> prereqNodeIds) {
        this.prereqNodeIds = prereqNodeIds;
    }

    /** Usado pela ingestão do currículo, que reescreve o nó a partir do JSON versionado. */
    public void update(
            CurriculumModule module,
            String title,
            Belt belt,
            String concept,
            int orderIndex,
            UnlockRule unlockRule,
            VideoRef video,
            List<ExtraVideoRef> extraVideos) {
        this.module = module;
        this.title = title;
        this.belt = belt;
        this.concept = concept;
        this.orderIndex = orderIndex;
        this.unlockRule = unlockRule;
        this.video = video;
        setExtraVideos(extraVideos);
    }

    /**
     * Substitui o conteúdo da lista em vez de trocar a referência: a coleção é gerenciada pelo JPA,
     * e atribuir uma lista nova a cada sync do currículo faria o Hibernate descartar e recriar a
     * coleção inteira de todos os 46 nós, mesmo os que não mudaram.
     */
    private void setExtraVideos(List<ExtraVideoRef> replacement) {
        this.extraVideos.clear();
        if (replacement != null) {
            this.extraVideos.addAll(replacement);
        }
    }
}
