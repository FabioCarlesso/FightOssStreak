package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.fos.model.UsageDaily;
import dev.fos.model.UsageDimension;
import dev.fos.model.UsageEvent;
import dev.fos.model.UsageEventType;
import dev.fos.repo.UsageDailyRepository;
import dev.fos.web.dto.AdminPanelDtos;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
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
 * A conta que o painel faz (#85, D50).
 *
 * <p>O {@link dev.fos.web.AdminPanelIntegrationTest} prova quem entra e o que a resposta não
 * carrega; aqui está o que os números significam — que é onde um painel erra sem avisar ninguém: um
 * ranking que perde a cauda, um comparativo que pega a janela errada, um percentual que afirma
 * queda onde não houve medição.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(UsagePanelServiceTest.FixedClockConfig.class)
@Transactional
class UsagePanelServiceTest {

    /** Hoje é 2026-08-27. O período de 7 dias vai, então, de 20/08 a 26/08. */
    private static final LocalDate HOJE = LocalDate.of(2026, 8, 27);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-27T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired private UsagePanelService painel;
    @Autowired private UsageDailyRepository daily;

    @BeforeEach
    void limpar() {
        daily.deleteAll();
    }

    @Test
    @DisplayName("o ranking soma a cauda em 'outros' em vez de descartá-la")
    void theTailIsSummedIntoOthers() {
        LocalDate ontem = HOJE.minusDays(1);
        // Doze origens distintas: oito cabem no ranking, quatro viram uma linha só.
        for (int i = 1; i <= 12; i++) {
            plantar(ontem, UsageDimension.ORIGEM, "origem-" + i, i, i);
        }

        List<AdminPanelDtos.Slice> origens = painel.painel(7).origins();

        assertThat(origens).hasSize(9);
        assertThat(origens.get(0).value()).isEqualTo("origem-12");
        assertThat(origens.get(0).total()).isEqualTo(12);
        assertThat(origens.get(8).value()).isEqualTo("outros");
        // 1 + 2 + 3 + 4: o que ficou de fora do topo, e não o que sobrou por acaso.
        assertThat(origens.get(8).total()).isEqualTo(10);
        assertThat(origens.stream().mapToLong(AdminPanelDtos.Slice::total).sum()).isEqualTo(78);
    }

    @Test
    @DisplayName("origem chamada 'outros' não vira duas linhas com o mesmo nome")
    void anOriginNamedOthersIsMergedIntoTheTail() {
        LocalDate ontem = HOJE.minusDays(1);
        // Uma origem literalmente chamada "outros" — `utm_source=outros` é um link que alguém pode
        // escrever — e grande o bastante para ficar no topo do ranking.
        plantar(ontem, UsageDimension.ORIGEM, "outros", 100, 50);
        for (int i = 1; i <= 12; i++) {
            plantar(ontem, UsageDimension.ORIGEM, "origem-" + i, i, i);
        }

        List<AdminPanelDtos.Slice> origens = painel.painel(7).origins();

        // Uma linha só com esse nome: a tela identifica cada linha pelo valor, e duas iguais
        // quebrariam a lista sem que nenhuma soma denunciasse.
        assertThat(origens).extracting(AdminPanelDtos.Slice::value).containsOnlyOnce("outros");
        // 100 da origem de verdade + 1 + 2 + 3 + 4 + 5 da cauda: ela foi somada, e não descartada.
        assertThat(origens)
                .filteredOn(fatia -> "outros".equals(fatia.value()))
                .singleElement()
                .satisfies(
                        fatia -> {
                            assertThat(fatia.total()).isEqualTo(115);
                            assertThat(fatia.visitors()).isEqualTo(65);
                        });
        // E o total continua fechando com tudo que foi plantado: 100 + (1..12).
        assertThat(origens.stream().mapToLong(AdminPanelDtos.Slice::total).sum()).isEqualTo(178);
    }

    @Test
    @DisplayName("país sem base de geolocalização vira categoria legível, não código")
    void unknownCountryIsACategory() {
        plantar(HOJE.minusDays(1), UsageDimension.PAIS, UsageEvent.PAIS_DESCONHECIDO, 5, 3);
        plantar(HOJE.minusDays(1), UsageDimension.PAIS, "BR", 9, 4);

        List<AdminPanelDtos.Slice> paises = painel.painel(7).profile().countries();

        assertThat(paises)
                .extracting(AdminPanelDtos.Slice::value)
                .containsExactly("BR", "desconhecido");
    }

    @Test
    @DisplayName("a conversão de cada degrau é a do degrau imediatamente anterior")
    void eachStepConvertsFromTheOneBefore() {
        LocalDate ontem = HOJE.minusDays(1);
        plantar(ontem, UsageDimension.EVENTO, UsageEventType.PAGINA.name(), 400, 100);
        plantar(ontem, UsageDimension.EVENTO, UsageEventType.DEMONSTRACAO_ABERTA.name(), 20, 18);
        plantar(ontem, UsageDimension.EVENTO, UsageEventType.CADASTRO_CRIADO.name(), 10, 10);
        plantar(ontem, UsageDimension.EVENTO, UsageEventType.EMAIL_VERIFICADO.name(), 5, 5);
        plantar(ontem, UsageDimension.EVENTO, UsageEventType.PRIMEIRO_DRILL.name(), 4, 4);
        plantar(ontem, UsageDimension.EVENTO, UsageEventType.RETORNO_EM_7_DIAS.name(), 1, 1);

        List<AdminPanelDtos.FunnelStep> funil = painel.painel(7).funnel();

        assertThat(funil)
                .extracting(AdminPanelDtos.FunnelStep::total)
                .containsExactly(100L, 20L, 10L, 5L, 4L, 1L);
        assertThat(funil)
                .extracting(AdminPanelDtos.FunnelStep::percentOfPrevious)
                .containsExactly(null, 20, 50, 50, 80, 25);
    }

    @Test
    @DisplayName("o perfil sai das dimensões de navegador e idioma, que entraram com o painel")
    void theProfileReadsBrowserAndLanguage() {
        LocalDate ontem = HOJE.minusDays(1);
        plantar(ontem, UsageDimension.DISPOSITIVO, "CELULAR", 12, 7);
        plantar(ontem, UsageDimension.NAVEGADOR, "chrome", 9, 6);
        plantar(ontem, UsageDimension.IDIOMA, "pt-br", 11, 7);
        plantar(ontem, UsageDimension.CAMINHO, "/", 30, 9);
        plantar(ontem, UsageDimension.CAMINHO, "/no/{codigo}", 4, 2);

        AdminPanelDtos.PanelView view = painel.painel(7);

        assertThat(view.profile().devices())
                .singleElement()
                .returns("CELULAR", AdminPanelDtos.Slice::value);
        assertThat(view.profile().browsers())
                .singleElement()
                .returns("chrome", AdminPanelDtos.Slice::value);
        assertThat(view.profile().languages())
                .singleElement()
                .returns("pt-br", AdminPanelDtos.Slice::value);
        assertThat(view.content())
                .extracting(AdminPanelDtos.Slice::value)
                .containsExactly("/", "/no/{codigo}");
    }

    @Test
    @DisplayName("o período de 30 dias não enxerga o dia que só cabe no de 90")
    void eachPresetCutsItsOwnWindow() {
        plantar(HOJE.minusDays(40), UsageDimension.EVENTO, UsageEventType.PAGINA.name(), 7, 7);

        assertThat(painel.painel(30).access().visits()).isZero();
        assertThat(painel.painel(90).access().visits()).isEqualTo(7);
        // No recorte de 30 dias aquele dia cai no período ANTERIOR, que é o que o comparativo mede.
        assertThat(painel.painel(30).access().previousVisits()).isEqualTo(7);
    }

    @Test
    @DisplayName("período fora dos três presets é recusado")
    void anArbitraryWindowIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> painel.painel(45));
        assertThrows(IllegalArgumentException.class, () -> painel.painel(0));
    }

    private void plantar(
            LocalDate dia, UsageDimension dimensao, String valor, long eventos, long visitas) {
        daily.save(new UsageDaily(dia, dimensao, valor, eventos, visitas));
    }
}
