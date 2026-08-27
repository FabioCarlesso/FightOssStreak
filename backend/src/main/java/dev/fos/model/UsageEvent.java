package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A linha crua da coleta de uso (#84, D50): um acesso, ou um degrau do funil.
 *
 * <p>Repare no que <b>não</b> está aqui: endereço de IP. Ele é lido na requisição, deriva país e
 * compõe {@link #visitKey}, e morre no método que o leu. Não há campo, não há coluna, e há teste
 * que falha se um aparecer.
 *
 * <p>A {@link #visitKey} é o que separa "100 acessos de uma pessoa" de "100 pessoas" sem cookie e
 * sem identificador estável: hash de (IP + User-Agent + sal do dia), com o sal sorteado por dia e
 * nunca gravado. A consequência — a mesma pessoa em dois dias não é ligável — é o preço combinado,
 * não um defeito.
 */
@Entity
@Table(name = "usage_event")
public class UsageEvent {

    /** Valor de país quando não há base de geolocalização, ou o IP não está nela. */
    public static final String PAIS_DESCONHECIDO = "ZZ";

    /** Valor usado em toda dimensão de texto que a requisição não trouxe. */
    public static final String DESCONHECIDO = "desconhecido";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private UsageEventType eventType;

    @Column(nullable = false)
    private String path;

    @Column(name = "visit_key", nullable = false)
    private String visitKey;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeviceClass device;

    @Column(nullable = false)
    private String browser;

    @Column(nullable = false)
    private String os;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String region;

    @Column(name = "referrer_host", nullable = false)
    private String referrerHost;

    @Column(name = "utm_source", nullable = false)
    private String utmSource;

    @Column(name = "utm_medium", nullable = false)
    private String utmMedium;

    @Column(name = "utm_campaign", nullable = false)
    private String utmCampaign;

    protected UsageEvent() {
        // JPA
    }

    public UsageEvent(
            Instant occurredAt,
            LocalDate occurredOn,
            UsageEventType eventType,
            String path,
            String visitKey,
            Long userId,
            DeviceClass device,
            String browser,
            String os,
            String language,
            String country,
            String region,
            String referrerHost,
            String utmSource,
            String utmMedium,
            String utmCampaign) {
        this.occurredAt = occurredAt;
        this.occurredOn = occurredOn;
        this.eventType = eventType;
        this.path = path;
        this.visitKey = visitKey;
        this.userId = userId;
        this.device = device;
        this.browser = browser;
        this.os = os;
        this.language = language;
        this.country = country;
        this.region = region;
        this.referrerHost = referrerHost;
        this.utmSource = utmSource;
        this.utmMedium = utmMedium;
        this.utmCampaign = utmCampaign;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public UsageEventType getEventType() {
        return eventType;
    }

    public String getPath() {
        return path;
    }

    public String getVisitKey() {
        return visitKey;
    }

    public Long getUserId() {
        return userId;
    }

    public DeviceClass getDevice() {
        return device;
    }

    public String getBrowser() {
        return browser;
    }

    public String getOs() {
        return os;
    }

    public String getLanguage() {
        return language;
    }

    public String getCountry() {
        return country;
    }

    public String getRegion() {
        return region;
    }

    public String getReferrerHost() {
        return referrerHost;
    }

    public String getUtmSource() {
        return utmSource;
    }

    public String getUtmMedium() {
        return utmMedium;
    }

    public String getUtmCampaign() {
        return utmCampaign;
    }

    /**
     * De onde veio, em uma palavra: a campanha quando há {@code utm_source}, senão o host do
     * referrer, senão "direto".
     *
     * <p>Mora na entidade, e não no agregador, porque é a definição de "origem" do projeto inteiro
     * — o painel (fatia 2) precisa concordar com o agregado, e duas implementações discordariam.
     */
    public String origin() {
        if (!utmSource.isBlank() && !DESCONHECIDO.equals(utmSource)) {
            return utmSource;
        }
        if (!referrerHost.isBlank() && !DESCONHECIDO.equals(referrerHost)) {
            return referrerHost;
        }
        return "direto";
    }
}
