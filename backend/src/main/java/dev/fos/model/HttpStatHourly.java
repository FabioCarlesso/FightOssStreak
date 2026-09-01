package dev.fos.model;

import dev.fos.service.HttpStats;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * O que aconteceu numa rota, numa hora (#86).
 *
 * <p>Uma linha por (hora, rota), e nada mais: quantas requisições, quantas erraram de cada lado,
 * quanto tempo somaram, e em que faixa da escada cada uma caiu. <b>Não há nesta tabela nada que
 * diga quem fez a requisição nem de onde ela veio</b> — nem conta, nem chave de visita, nem
 * endereço. Contar requisição não é observar pessoa, e é essa fronteira que mantém de pé a promessa
 * de {@code docs/11-privacidade.md}.
 *
 * <p>O {@code path} é o <b>padrão</b> que o roteamento casou, nunca o caminho que chegou: é a mesma
 * guarda que o {@code UsagePaths} faz na coleta de uso, por outro caminho. Padrão não tem segmento
 * variável, então token de confirmação de e-mail não tem por onde entrar aqui.
 */
@Entity
@Table(name = "http_stat_hourly")
public class HttpStatHourly {

    /** O que se grava quando a requisição não casou rota nenhuma — um 404, tipicamente. */
    public static final String SEM_ROTA = "(sem rota)";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hour_start", nullable = false)
    private Instant hourStart;

    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private long requests;

    @Column(name = "client_errors", nullable = false)
    private long clientErrors;

    @Column(name = "server_errors", nullable = false)
    private long serverErrors;

    @Column(name = "total_ms", nullable = false)
    private long totalMs;

    @Column(name = "max_ms", nullable = false)
    private long maxMs;

    // As oito faixas da escada de HttpStats. Colunas explícitas, e não formato longo, porque a
    // escada é do programa: mudar o número de faixas é migration de propósito.
    @Column(name = "bucket_25", nullable = false)
    private long bucket25;

    @Column(name = "bucket_50", nullable = false)
    private long bucket50;

    @Column(name = "bucket_100", nullable = false)
    private long bucket100;

    @Column(name = "bucket_250", nullable = false)
    private long bucket250;

    @Column(name = "bucket_500", nullable = false)
    private long bucket500;

    @Column(name = "bucket_1000", nullable = false)
    private long bucket1000;

    @Column(name = "bucket_2500", nullable = false)
    private long bucket2500;

    @Column(name = "bucket_acima", nullable = false)
    private long bucketAcima;

    protected HttpStatHourly() {
        // JPA
    }

    public HttpStatHourly(
            Instant hourStart,
            String path,
            long requests,
            long clientErrors,
            long serverErrors,
            long totalMs,
            long maxMs,
            long[] histograma) {
        this.hourStart = hourStart;
        this.path = path;
        this.requests = requests;
        this.clientErrors = clientErrors;
        this.serverErrors = serverErrors;
        this.totalMs = totalMs;
        this.maxMs = maxMs;
        setHistograma(histograma);
    }

    /**
     * Soma outra medição nesta linha.
     *
     * <p>Existe porque o flush pode encontrar a hora já gravada — o job roda várias vezes dentro da
     * mesma hora, e reiniciar o app no meio dela também deixa metade escrita. Somar em cima é o que
     * faz a linha ser o total da hora, e não a última rodada do job.
     */
    public void somar(
            long requests,
            long clientErrors,
            long serverErrors,
            long totalMs,
            long maxMs,
            long[] histograma) {
        this.requests += requests;
        this.clientErrors += clientErrors;
        this.serverErrors += serverErrors;
        this.totalMs += totalMs;
        this.maxMs = Math.max(this.maxMs, maxMs);
        long[] atual = histograma();
        for (int i = 0; i < atual.length; i++) {
            atual[i] += histograma[i];
        }
        setHistograma(atual);
    }

    public Long getId() {
        return id;
    }

    public Instant getHourStart() {
        return hourStart;
    }

    public String getPath() {
        return path;
    }

    public long getRequests() {
        return requests;
    }

    public long getClientErrors() {
        return clientErrors;
    }

    public long getServerErrors() {
        return serverErrors;
    }

    public long getTotalMs() {
        return totalMs;
    }

    public long getMaxMs() {
        return maxMs;
    }

    /** O histograma como vetor, para que o resto do código não conheça as oito colunas. */
    public long[] histograma() {
        return new long[] {
            bucket25, bucket50, bucket100, bucket250, bucket500, bucket1000, bucket2500, bucketAcima
        };
    }

    private void setHistograma(long[] faixas) {
        if (faixas.length != HttpStats.FAIXAS) {
            throw new IllegalArgumentException(
                    "histograma precisa ter " + HttpStats.FAIXAS + " faixas");
        }
        bucket25 = faixas[0];
        bucket50 = faixas[1];
        bucket100 = faixas[2];
        bucket250 = faixas[3];
        bucket500 = faixas[4];
        bucket1000 = faixas[5];
        bucket2500 = faixas[6];
        bucketAcima = faixas[7];
    }
}
