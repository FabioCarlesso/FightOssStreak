package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * A contagem de um dia, por uma dimensão (#84, D50).
 *
 * <p>É o que o painel lê, e é o que sobrevive ao expurgo dos 90 dias: aqui não há chave de visita,
 * id de conta nem qualquer coisa que aponte para alguém — só quantos eventos e quantas visitas
 * distintas houve. É por isso que ela pode ficar para sempre e a tabela crua não pode.
 */
@Entity
@Table(name = "usage_daily")
public class UsageDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UsageDimension dimension;

    @Column(name = "dimension_value", nullable = false)
    private String value;

    @Column(nullable = false)
    private long events;

    @Column(nullable = false)
    private long visits;

    protected UsageDaily() {
        // JPA
    }

    public UsageDaily(
            LocalDate occurredOn,
            UsageDimension dimension,
            String value,
            long events,
            long visits) {
        this.occurredOn = occurredOn;
        this.dimension = dimension;
        this.value = value;
        this.events = events;
        this.visits = visits;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public UsageDimension getDimension() {
        return dimension;
    }

    public String getValue() {
        return value;
    }

    public long getEvents() {
        return events;
    }

    public long getVisits() {
        return visits;
    }
}
