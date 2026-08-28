package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.DeviceClass;
import dev.fos.model.UsageDaily;
import dev.fos.model.UsageDimension;
import dev.fos.model.UsageEvent;
import dev.fos.model.UsageEventType;
import dev.fos.repo.UsageDailyRepository;
import dev.fos.repo.UsageEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * A agregação diária e o expurgo dos 90 dias (#84, D50).
 *
 * <p>Dois critérios de aceite da issue moram aqui: o expurgo apaga evento cru com mais de 90 dias e
 * <b>preserva o agregado</b>, e país desconhecido é categoria própria em vez de erro.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(UsageAggregatorTest.FixedClockConfig.class)
@Transactional
class UsageAggregatorTest {

    /** Hoje é 2026-08-27. Os dias plantados abaixo são relativos a ele. */
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 27);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private UsageAggregator aggregator;
    @Autowired private UsageEventRepository events;
    @Autowired private UsageDailyRepository daily;

    @BeforeEach
    void limpar() {
        events.deleteAll();
        daily.deleteAll();
    }

    @Test
    @DisplayName("fecha os dias anteriores contando eventos e visitas distintas por dimensão")
    void aggregatesByDimension() {
        LocalDate ontem = HOJE.minusDays(1);
        // Duas visitas, três acessos: é a diferença entre "3 acessos" e "2 pessoas", que é a única
        // coisa que a chave de visita existe para responder.
        plantar(
                ontem,
                "visita-a",
                UsageEventType.PAGINA,
                "/",
                "BR",
                DeviceClass.CELULAR,
                "whatsapp");
        plantar(
                ontem,
                "visita-a",
                UsageEventType.PAGINA,
                "/",
                "BR",
                DeviceClass.CELULAR,
                "whatsapp");
        plantar(ontem, "visita-b", UsageEventType.PAGINA, "/hoje", "PT", DeviceClass.DESKTOP, null);

        assertThat(aggregator.aggregatePending()).isEqualTo(1);

        assertThat(linha(ontem, UsageDimension.CAMINHO, "/"))
                .get()
                .satisfies(
                        l -> {
                            assertThat(l.getEvents()).isEqualTo(2);
                            assertThat(l.getVisits()).isEqualTo(1);
                        });
        assertThat(linha(ontem, UsageDimension.PAIS, "BR"))
                .get()
                .extracting(UsageDaily::getEvents)
                .isEqualTo(2L);
        assertThat(linha(ontem, UsageDimension.PAIS, "PT"))
                .get()
                .extracting(UsageDaily::getEvents)
                .isEqualTo(1L);
        assertThat(linha(ontem, UsageDimension.DISPOSITIVO, "CELULAR"))
                .get()
                .extracting(UsageDaily::getVisits)
                .isEqualTo(1L);
        assertThat(linha(ontem, UsageDimension.ORIGEM, "whatsapp"))
                .get()
                .extracting(UsageDaily::getEvents)
                .isEqualTo(2L);
        // Sem utm e sem referrer: a origem é "direto", e não uma linha em branco no painel.
        assertThat(linha(ontem, UsageDimension.ORIGEM, "direto")).isPresent();
    }

    @Test
    @DisplayName("o funil é lido pela dimensão EVENTO, e não polui caminho nem origem")
    void funnelCountsSeparately() {
        LocalDate ontem = HOJE.minusDays(1);
        plantar(
                ontem,
                "visita-a",
                UsageEventType.PAGINA,
                "/cadastrar",
                "BR",
                DeviceClass.CELULAR,
                null);
        plantar(
                ontem,
                "visita-a",
                UsageEventType.CADASTRO_CRIADO,
                UsageCollector.SEM_CAMINHO,
                "BR",
                DeviceClass.CELULAR,
                null);

        aggregator.aggregatePending();

        assertThat(linha(ontem, UsageDimension.EVENTO, "CADASTRO_CRIADO"))
                .get()
                .extracting(UsageDaily::getEvents)
                .isEqualTo(1L);
        // O caminho-sentinela do evento de funil não aparece na dimensão de caminho.
        assertThat(linha(ontem, UsageDimension.CAMINHO, UsageCollector.SEM_CAMINHO)).isEmpty();
        assertThat(linha(ontem, UsageDimension.CAMINHO, "/cadastrar")).isPresent();
    }

    @Test
    @DisplayName("país desconhecido é categoria própria — é como dev e CI coletam")
    void unknownCountryIsACategory() {
        LocalDate ontem = HOJE.minusDays(1);
        plantar(
                ontem,
                "visita-a",
                UsageEventType.PAGINA,
                "/",
                UsageEvent.PAIS_DESCONHECIDO,
                DeviceClass.DESCONHECIDO,
                null);

        aggregator.aggregatePending();

        assertThat(linha(ontem, UsageDimension.PAIS, UsageEvent.PAIS_DESCONHECIDO))
                .get()
                .extracting(UsageDaily::getEvents)
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("o dia de hoje não é fechado: ele ainda recebe evento")
    void todayIsNotClosed() {
        plantar(HOJE, "visita-a", UsageEventType.PAGINA, "/", "BR", DeviceClass.CELULAR, null);

        assertThat(aggregator.aggregatePending()).isZero();
        assertThat(daily.findByOccurredOn(HOJE)).isEmpty();
    }

    @Test
    @DisplayName("rodar duas vezes não conta em dobro")
    void isIdempotent() {
        LocalDate ontem = HOJE.minusDays(1);
        plantar(ontem, "visita-a", UsageEventType.PAGINA, "/", "BR", DeviceClass.CELULAR, null);

        aggregator.aggregatePending();
        assertThat(aggregator.aggregatePending()).isZero();

        // E reagregar de propósito refaz do zero, em vez de somar em cima.
        aggregator.aggregate(ontem);
        assertThat(linha(ontem, UsageDimension.CAMINHO, "/"))
                .get()
                .extracting(UsageDaily::getEvents)
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("o expurgo apaga o cru de mais de 90 dias e PRESERVA o agregado")
    void purgeKeepsTheAggregate() {
        LocalDate antigo = HOJE.minusDays(120);
        LocalDate recente = HOJE.minusDays(10);
        plantar(
                antigo,
                "visita-velha",
                UsageEventType.PAGINA,
                "/",
                "BR",
                DeviceClass.CELULAR,
                null);
        plantar(
                recente,
                "visita-nova",
                UsageEventType.PAGINA,
                "/",
                "BR",
                DeviceClass.CELULAR,
                null);
        aggregator.aggregatePending();

        assertThat(aggregator.purge()).isEqualTo(1);

        assertThat(events.findByOccurredOn(antigo)).isEmpty();
        assertThat(events.findByOccurredOn(recente)).hasSize(1);
        // O agregado do dia antigo continua de pé: é o histórico, e não tem dado de pessoa nenhuma.
        assertThat(linha(antigo, UsageDimension.CAMINHO, "/")).isPresent();
    }

    private Optional<UsageDaily> linha(LocalDate dia, UsageDimension dimensao, String valor) {
        List<UsageDaily> doDia = daily.findByOccurredOn(dia);
        return doDia.stream()
                .filter(l -> l.getDimension() == dimensao && l.getValue().equals(valor))
                .findFirst();
    }

    private void plantar(
            LocalDate dia,
            String visita,
            UsageEventType tipo,
            String caminho,
            String pais,
            DeviceClass dispositivo,
            String utmSource) {
        events.save(
                new UsageEvent(
                        dia.atTime(12, 0).toInstant(ZoneOffset.UTC),
                        dia,
                        tipo,
                        caminho,
                        visita,
                        null,
                        dispositivo,
                        "chrome",
                        "android",
                        "pt",
                        pais,
                        UsageEvent.DESCONHECIDO,
                        UsageEvent.DESCONHECIDO,
                        utmSource == null ? UsageEvent.DESCONHECIDO : utmSource,
                        UsageEvent.DESCONHECIDO,
                        UsageEvent.DESCONHECIDO));
    }
}
