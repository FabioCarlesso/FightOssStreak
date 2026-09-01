package dev.fos.service;

import dev.fos.model.HttpStatHourly;
import dev.fos.repo.HttpStatHourlyRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que a aplicação sabe de si: quantas requisições, com que status, em quanto tempo (#86).
 *
 * <p><b>Em memória, com descarga periódica.</b> Gravar uma linha por requisição transformaria o
 * monitoramento no maior escritor do banco — mais tráfego de escrita que o app inteiro — para
 * responder perguntas que são todas agregadas. O que fica na memória é um punhado de contadores por
 * (hora, rota); o que vai para o banco é a soma deles.
 *
 * <p><b>Nada disso pode quebrar tela.</b> Como na coleta de uso (#84), toda entrada aqui engole a
 * própria falha: uma medição perdida é uma medição perdida, nunca uma requisição perdida.
 *
 * <p><b>Nem uma linha daqui sabe quem chamou.</b> Não há conta, não há chave de visita, não há
 * endereço — a mesma régua da D50, e o mesmo motivo: contar requisição não é observar pessoa.
 *
 * <p>Duas estruturas, com vidas diferentes:
 *
 * <ul>
 *   <li>o <b>buffer por hora</b>, que é o que vira linha de {@code http_stat_hourly} e responde o
 *       painel;
 *   <li>a <b>janela por minuto</b>, curta e circular, que é o que o alerta lê. Ela existe porque o
 *       alerta pergunta "e nos últimos quinze minutos?", e a granularidade do que foi gravado é a
 *       hora — perguntar isso ao banco só daria resposta certa uma vez por hora.
 * </ul>
 */
@Service
public class HttpStatCollector {

    private static final Logger log = LoggerFactory.getLogger(HttpStatCollector.class);

    /**
     * Teto de rotas distintas guardadas ao mesmo tempo.
     *
     * <p>As chaves são <b>padrões</b> de rota, e a lista deles é fechada pelo próprio código — o
     * teto não é para o caso normal, é para o dia em que alguém registrar um mapeamento que gere
     * padrão variável. Estourado o teto, a medição do excedente é descartada e o app continua
     * atendendo, que é a ordem de prioridade certa.
     */
    private static final int TETO_DE_ROTAS = 500;

    /** Quanto tempo a janela por minuto guarda. Folga sobre a maior janela de alerta plausível. */
    private static final int MINUTOS_GUARDADOS = 120;

    /** Contadores de uma (hora, rota). Mutável e sincronizado: é escrito em toda requisição. */
    private static final class Acumulado {
        long requests;
        long clientErrors;
        long serverErrors;
        long totalMs;
        long maxMs;
        final long[] histograma = new long[HttpStats.FAIXAS];

        synchronized void somar(int status, long millis) {
            requests++;
            if (status >= 500) {
                serverErrors++;
            } else if (status >= 400) {
                clientErrors++;
            }
            totalMs += millis;
            maxMs = Math.max(maxMs, millis);
            histograma[HttpStats.faixa(millis)]++;
        }
    }

    /** O recorte de um minuto, para o alerta. Só três números — ele não pergunta por rota. */
    private static final class Minuto {
        long requests;
        long serverErrors;
        long authRejects;

        synchronized void somar(int status) {
            requests++;
            if (status >= 500) {
                serverErrors++;
            } else if (status == 401 || status == 403) {
                authRejects++;
            }
        }
    }

    /** Um recorte da janela do alerta. */
    public record Janela(long requests, long serverErrors, long authRejects) {

        /** A taxa de 5xx em pontos percentuais. Sem requisição não há taxa, e zero é a resposta. */
        public int errorRatePercent() {
            return requests == 0 ? 0 : Math.toIntExact(Math.round(serverErrors * 100.0 / requests));
        }
    }

    private record Chave(Instant hora, String path) {}

    private final HttpStatHourlyRepository repository;
    private final Clock clock;

    private final Map<Chave, Acumulado> buffer = new ConcurrentHashMap<>();
    private final Map<Instant, Minuto> porMinuto = new ConcurrentHashMap<>();

    public HttpStatCollector(HttpStatHourlyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Uma requisição medida.
     *
     * @param path o <b>padrão</b> da rota casada, nunca o caminho que chegou
     * @param status o status final da resposta
     * @param millis quanto tempo a requisição levou
     */
    public void record(String path, int status, long millis) {
        try {
            Instant agora = Instant.now(clock);
            Chave chave = new Chave(agora.truncatedTo(ChronoUnit.HOURS), path);
            Acumulado acumulado = buffer.get(chave);
            if (acumulado == null) {
                if (buffer.size() >= TETO_DE_ROTAS) {
                    return;
                }
                acumulado = buffer.computeIfAbsent(chave, ignorado -> new Acumulado());
            }
            acumulado.somar(status, Math.max(millis, 0));

            Instant minuto = agora.truncatedTo(ChronoUnit.MINUTES);
            porMinuto.computeIfAbsent(minuto, ignorado -> new Minuto()).somar(status);
            porMinuto
                    .keySet()
                    .removeIf(m -> m.isBefore(minuto.minus(Duration.ofMinutes(MINUTOS_GUARDADOS))));
        } catch (RuntimeException falha) {
            // `debug` e não `warn`: se isto quebrar, quebra a cada requisição — e um log por
            // requisição transforma um problema de métrica em um problema de operação.
            log.debug("Medição de requisição não registrada", falha);
        }
    }

    /**
     * O que aconteceu nos últimos {@code minutos} — a leitura do alerta.
     *
     * <p>O minuto corrente entra: ele está incompleto, e é justamente onde o incidente que começou
     * agora aparece primeiro.
     */
    public Janela janela(int minutos) {
        Instant limite =
                Instant.now(clock)
                        .truncatedTo(ChronoUnit.MINUTES)
                        .minus(Duration.ofMinutes(minutos - 1L));
        long requests = 0;
        long serverErrors = 0;
        long authRejects = 0;
        for (Map.Entry<Instant, Minuto> entrada : porMinuto.entrySet()) {
            if (entrada.getKey().isBefore(limite)) {
                continue;
            }
            Minuto minuto = entrada.getValue();
            synchronized (minuto) {
                requests += minuto.requests;
                serverErrors += minuto.serverErrors;
                authRejects += minuto.authRejects;
            }
        }
        return new Janela(requests, serverErrors, authRejects);
    }

    /**
     * Descarrega o buffer no banco e devolve quantas (hora, rota) foram escritas.
     *
     * <p>Tira a chave do mapa <b>antes</b> de escrever: requisição que chegar durante a escrita
     * começa um acumulado novo em vez de ter a contagem apagada por baixo. O preço é que uma falha
     * de banco perde a rodada — e perder medição é o comportamento certo aqui, contra segurar
     * memória à espera de um banco que pode não voltar.
     *
     * <p>Soma na linha existente quando ela já está lá: o job roda várias vezes dentro da mesma
     * hora, e reiniciar o app no meio dela deixa metade da hora escrita.
     */
    @Transactional
    public int flush() {
        List<Map.Entry<Chave, Acumulado>> rodada = new ArrayList<>();
        for (Chave chave : List.copyOf(buffer.keySet())) {
            Acumulado acumulado = buffer.remove(chave);
            if (acumulado != null) {
                rodada.add(Map.entry(chave, acumulado));
            }
        }
        if (rodada.isEmpty()) {
            return 0;
        }

        // Uma cópia estável de cada acumulado: ele ainda pode estar sendo somado por uma requisição
        // que entrou entre o `remove` e esta linha.
        Map<Chave, long[]> numeros = new HashMap<>();
        Map<Chave, long[]> histogramas = new HashMap<>();
        for (Map.Entry<Chave, Acumulado> entrada : rodada) {
            Acumulado acumulado = entrada.getValue();
            synchronized (acumulado) {
                numeros.put(
                        entrada.getKey(),
                        new long[] {
                            acumulado.requests,
                            acumulado.clientErrors,
                            acumulado.serverErrors,
                            acumulado.totalMs,
                            acumulado.maxMs
                        });
                histogramas.put(entrada.getKey(), acumulado.histograma.clone());
            }
        }

        List<HttpStatHourly> linhas = new ArrayList<>(numeros.size());
        numeros.forEach(
                (chave, valores) -> {
                    long[] histograma = histogramas.get(chave);
                    HttpStatHourly linha =
                            repository
                                    .findByHourStartAndPath(chave.hora(), chave.path())
                                    .orElse(null);
                    if (linha == null) {
                        linhas.add(
                                new HttpStatHourly(
                                        chave.hora(),
                                        chave.path(),
                                        valores[0],
                                        valores[1],
                                        valores[2],
                                        valores[3],
                                        valores[4],
                                        histograma));
                        return;
                    }
                    linha.somar(
                            valores[0], valores[1], valores[2], valores[3], valores[4], histograma);
                    linhas.add(linha);
                });
        repository.saveAll(linhas);
        return linhas.size();
    }
}
