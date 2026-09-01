package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.HttpStatHourly;
import dev.fos.repo.HttpStatHourlyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * O que é contado, e o que é gravado (#86).
 *
 * <p>Relógio fixo e descarga chamada à mão: o job só decide <em>quando</em>, e o que este teste
 * precisa provar é <em>o quê</em> — que o status certo entra no balde certo, que a hora é o
 * recorte, e que uma segunda descarga na mesma hora soma em vez de sobrescrever. Esse último caso é
 * o que acontece de verdade a cada cinco minutos em produção, e é o mais fácil de escrever errado.
 *
 * <p>O coletor é <b>construído por caso</b>, e não injetado: ele guarda contadores em memória de
 * propósito, e o bean do contexto é o mesmo para a classe inteira — um caso herdaria a janela do
 * anterior e a asserção seria sobre a soma dos dois. Só o repositório vem do Spring, porque é dele
 * que se quer o banco de verdade.
 */
@SpringBootTest
@ActiveProfiles("test")
class HttpStatCollectorTest {

    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2026-08-27T10:20:00Z"), ZoneOffset.UTC);

    @Autowired private HttpStatHourlyRepository repository;

    private HttpStatCollector collector;

    @BeforeEach
    void montar() {
        repository.deleteAll();
        collector = new HttpStatCollector(repository, RELOGIO);
    }

    @Test
    @DisplayName("2xx, 4xx e 5xx caem em contadores diferentes da mesma rota")
    void eachStatusClassHasItsOwnCounter() {
        collector.record("/api/arvore", 200, 12);
        collector.record("/api/arvore", 204, 8);
        collector.record("/api/arvore", 404, 5);
        collector.record("/api/arvore", 500, 300);

        assertThat(collector.flush()).isEqualTo(1);

        HttpStatHourly linha = repository.findAll().get(0);
        assertThat(linha.getPath()).isEqualTo("/api/arvore");
        assertThat(linha.getRequests()).isEqualTo(4);
        assertThat(linha.getClientErrors()).isEqualTo(1);
        assertThat(linha.getServerErrors()).isEqualTo(1);
        assertThat(linha.getTotalMs()).isEqualTo(325);
        assertThat(linha.getMaxMs()).isEqualTo(300);
        // 12, 8 e 5 ms caem na primeira faixa; 300 ms na de 500.
        assertThat(linha.histograma()[0]).isEqualTo(3);
        assertThat(linha.histograma()[4]).isEqualTo(1);
    }

    @Test
    @DisplayName("a hora é o recorte: a linha de uma (hora, rota) é somada, nunca sobrescrita")
    void asecondFlushInTheSameHourAddsUp() {
        collector.record("/api/hoje", 200, 10);
        collector.flush();

        // Segunda rodada dentro da mesma hora — é o que o job faz a cada cinco minutos.
        collector.record("/api/hoje", 200, 20);
        collector.record("/api/hoje", 500, 30);
        collector.flush();

        List<HttpStatHourly> linhas = repository.findAll();
        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getHourStart()).isEqualTo(Instant.parse("2026-08-27T10:00:00Z"));
        assertThat(linhas.get(0).getRequests()).isEqualTo(3);
        assertThat(linhas.get(0).getServerErrors()).isEqualTo(1);
        assertThat(linhas.get(0).getTotalMs()).isEqualTo(60);
    }

    @Test
    @DisplayName("descarga com o buffer vazio não escreve nada")
    void anEmptyFlushWritesNothing() {
        assertThat(collector.flush()).isZero();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("a janela do alerta conta 5xx e 401/403 separados, e sai da memória")
    void theAlertWindowSeparatesErrorsFromRefusals() {
        collector.record("/api/hoje", 200, 5);
        collector.record("/api/hoje", 500, 5);
        collector.record("/api/hoje", 401, 5);
        collector.record("/api/hoje", 403, 5);
        // 404 não é recusa de credencial nem erro do app: não entra em nenhum dos dois.
        collector.record("/api/hoje", 404, 5);

        HttpStatCollector.Janela janela = collector.janela(15);
        assertThat(janela.requests()).isEqualTo(5);
        assertThat(janela.serverErrors()).isEqualTo(1);
        assertThat(janela.authRejects()).isEqualTo(2);
        assertThat(janela.errorRatePercent()).isEqualTo(20);

        // A janela é de memória e independe da descarga: ela responde "e nos últimos 15 minutos?",
        // e o que foi gravado tem granularidade de hora.
        collector.flush();
        assertThat(collector.janela(15).requests()).isEqualTo(5);
    }

    @Test
    @DisplayName("sem requisição nenhuma a taxa de erro é zero, e não uma divisão por zero")
    void anEmptyWindowHasNoRate() {
        assertThat(collector.janela(15).errorRatePercent()).isZero();
    }
}
