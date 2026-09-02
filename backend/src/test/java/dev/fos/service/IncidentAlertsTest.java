package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.fos.config.FosProperties;
import dev.fos.email.EmailSender;
import dev.fos.repo.HttpStatHourlyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * A trava do alerta (#86).
 *
 * <p>É este teste que separa um alerta de um filtro no Gmail. O comportamento que ele fixa é o que
 * a D38 já tinha pago caro para aprender: <b>um e-mail por incidente</b>, e não um por janela.
 * Enquanto a condição durar, nada mais sai; quando ela passar, a trava abre e um incidente novo
 * pode avisar de novo.
 *
 * <p>Sem contexto do Spring, e com um relógio que o teste move à mão. Ele fica parado enquanto se
 * prova que a trava é <b>por incidente</b> e não por tempo, e anda vinte minutos quando o que se
 * quer é a janela esvaziar — que é como o incidente termina de verdade: não porque alguém apagou um
 * contador, mas porque os minutos ruins saíram da janela.
 *
 * <p>O repositório é um dublê porque o alerta nunca o toca: ele lê a janela de <b>memória</b>, e é
 * essa a decisão que o teste também documenta — o que foi gravado tem granularidade de hora, e
 * perguntar ao banco "e nos últimos quinze minutos?" só daria resposta certa uma vez por hora.
 */
class IncidentAlertsTest {

    private record Enviado(String to, String subject, String body) {}

    /** Um relógio que o teste move. O produto só o lê — é a mesma interface do {@link Clock}. */
    private static final class RelogioMovel extends Clock {
        private Instant agora = Instant.parse("2026-08-27T10:20:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }

        void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }
    }

    private final List<Enviado> enviados = new ArrayList<>();

    private RelogioMovel relogio;
    private HttpStatCollector collector;
    private IncidentAlerts alerts;

    @BeforeEach
    void montar() {
        enviados.clear();
        relogio = new RelogioMovel();
        collector = new HttpStatCollector(mock(HttpStatHourlyRepository.class), relogio);
        alerts = new IncidentAlerts(collector, comEnvio(), propriedades());
    }

    @Test
    @DisplayName("dentro do limiar não envia nada")
    void belowTheThresholdNothingIsSent() {
        // 30 requisições, 1 delas com erro: 3%, abaixo dos 10% do default.
        registrar(29, 200);
        registrar(1, 500);

        assertThat(alerts.verificar()).isZero();
        assertThat(enviados).isEmpty();
    }

    @Test
    @DisplayName("poucas requisições não viram incidente, ainda que todas errem")
    void aTinySampleIsNotAnIncident() {
        // 100% de erro — e mesmo assim nada sai: três requisições não medem taxa nenhuma, e o
        // alerta mais barulhento possível seria o primeiro a virar filtro no Gmail.
        registrar(3, 500);

        assertThat(alerts.verificar()).isZero();
        assertThat(enviados).isEmpty();
    }

    @Test
    @DisplayName(
            "acima do limiar envia uma vez, e a janela seguinte do mesmo incidente não reenvia")
    void anIncidentSendsExactlyOneEmail() {
        registrar(20, 500);
        registrar(10, 200);

        assertThat(alerts.verificar()).isEqualTo(1);
        assertThat(enviados).hasSize(1);
        assertThat(enviados.get(0).to()).isEqualTo("dono@example.test");
        assertThat(enviados.get(0).subject()).contains("Taxa de erro");
        assertThat(enviados.get(0).body()).contains("67%");

        // O incidente continua. Uma segunda verificação com a mesma condição não pode mandar nada.
        assertThat(alerts.verificar()).isZero();
        assertThat(enviados).hasSize(1);
    }

    @Test
    @DisplayName("depois de voltar ao normal, um incidente novo volta a avisar")
    void afterRecoveryANewIncidentAlertsAgain() {
        registrar(20, 500);
        registrar(10, 200);
        assertThat(alerts.verificar()).isEqualTo(1);

        // Vinte minutos depois os minutos ruins saíram da janela de quinze. Voltar ao normal não
        // manda e-mail nenhum: quem recebeu o primeiro vai olhar o painel de qualquer forma, e um
        // segundo e-mail dobra o ruído para dizer que acabou.
        relogio.avancar(Duration.ofMinutes(20));
        registrar(30, 200);
        assertThat(alerts.verificar()).isZero();
        assertThat(enviados).hasSize(1);

        relogio.avancar(Duration.ofMinutes(20));
        registrar(20, 500);
        registrar(10, 200);
        assertThat(alerts.verificar()).isEqualTo(1);
        assertThat(enviados).hasSize(2);
    }

    @Test
    @DisplayName("pico de 401/403 é incidente próprio, e não entra na taxa de erro")
    void aRefusalSpikeIsItsOwnIncident() {
        registrar(60, 401);

        assertThat(alerts.verificar()).isEqualTo(1);
        assertThat(enviados).hasSize(1);
        assertThat(enviados.get(0).subject()).contains("401/403");
        // Recusa não é o app errando: a taxa de 5xx continua zerada.
        assertThat(collector.janela(15).errorRatePercent()).isZero();
    }

    @Test
    @DisplayName("sem credencial de envio nada é registrado, e nada explode")
    void withoutAnEmailSenderNothingHappens() {
        // O caso de dev e do CI (regra 4 do CLAUDE.md): sem provedor de envio, o alerta é silêncio,
        // não erro.
        IncidentAlerts semEnvio = new IncidentAlerts(collector, semEnvio(), propriedades());
        registrar(20, 500);

        assertThat(semEnvio.verificar()).isZero();
        assertThat(enviados).isEmpty();
    }

    @Test
    @DisplayName("sem destinatário configurado o alerta se cala")
    void withoutRecipientsNothingIsSent() {
        FosProperties semDono =
                new FosProperties(
                        null,
                        null,
                        new FosProperties.Auth(List.of(), null),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);
        IncidentAlerts semDestinatario = new IncidentAlerts(collector, comEnvio(), semDono);
        registrar(20, 500);

        assertThat(semDestinatario.verificar()).isZero();
        assertThat(enviados).isEmpty();
    }

    private void registrar(int quantas, int status) {
        for (int i = 0; i < quantas; i++) {
            collector.record("/api/hoje", status, 5);
        }
    }

    private static FosProperties propriedades() {
        return new FosProperties(
                null,
                null,
                new FosProperties.Auth(List.of("dono@example.test"), null),
                null,
                null,
                null,
                null,
                null,
                null);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<EmailSender> comEnvio() {
        ObjectProvider<EmailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable())
                .thenReturn((to, subject, body) -> enviados.add(new Enviado(to, subject, body)));
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<EmailSender> semEnvio() {
        ObjectProvider<EmailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
