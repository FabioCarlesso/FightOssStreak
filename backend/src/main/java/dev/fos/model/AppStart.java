package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Uma subida da aplicação (#86).
 *
 * <p>Uma linha por vez que o app ficou pronto para atender. Parece pouco, e é justamente o que
 * transforma "acho que reiniciou de madrugada" em fato: o log da plataforma rotaciona, o histórico
 * de deploys não guarda o restart que ninguém pediu, e sem esta linha uma queda de dois minutos às
 * 3h não deixa rastro nenhum.
 *
 * <p>Não há linha de <em>parada</em>, e não é esquecimento: o processo que morre por OOM ou por
 * queda da máquina não escreve nada, então uma tabela de paradas só registraria as saídas educadas
 * — exatamente as que não interessam. O que se lê é a distância entre duas subidas.
 */
@Entity
@Table(name = "app_start")
public class AppStart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private String profiles;

    protected AppStart() {
        // JPA
    }

    public AppStart(Instant startedAt, String profiles) {
        this.startedAt = startedAt;
        this.profiles = profiles;
    }

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public String getProfiles() {
        return profiles;
    }
}
