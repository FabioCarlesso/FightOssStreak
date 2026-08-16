package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "curriculum_module")
public class CurriculumModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    protected CurriculumModule() {
        // JPA
    }

    public CurriculumModule(String code, String title, String summary, int orderIndex) {
        this.code = code;
        this.title = title;
        this.summary = summary;
        this.orderIndex = orderIndex;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    /** Usado pela ingestão do currículo, que reescreve o módulo a partir do JSON versionado. */
    public void update(String title, String summary, int orderIndex) {
        this.title = title;
        this.summary = summary;
        this.orderIndex = orderIndex;
    }
}
