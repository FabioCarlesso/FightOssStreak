package dev.fos.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * A saúde do site, como o painel a lê (#86).
 *
 * <p>Vale aqui a mesma leitura pela ausência que vale no {@link AdminPanelDtos}: <b>não há e-mail,
 * nome, id de conta nem endereço</b>. Tudo é contagem de requisição. Uma tela que dissesse "estes
 * erros foram da conta X" seria a D50 revertida por outra porta — e é a mesma porta, porque a
 * origem de uma requisição é a coisa que este app decidiu não guardar.
 *
 * <p>O que esta resposta <b>não</b> responde, e não é omissão: se o site ficou <em>fora do ar</em>.
 * Aplicação parada não escreve estatística, e o zero de uma hora sem linha nenhuma é indistinguível
 * de uma madrugada sem visita. Quem responde isso é a verificação de fora ({@code
 * .github/workflows/saude.yml}), e é por isso que ela existe.
 */
public final class AdminHealthDtos {

    private AdminHealthDtos() {}

    /**
     * @param hours o preset pedido: 24, 72 ou 168 horas
     * @param from primeira hora do período (início da hora, em UTC)
     * @param to última hora do período — a hora corrente, ainda em curso
     * @param collectedThrough hora mais recente que tem medição, ou {@code null} se não há nenhuma.
     *     É o que separa "ninguém chamou" de "a descarga ainda não rodou" — as duas dão zero
     * @param requests requisições medidas no período
     * @param serverErrors respostas 5xx no período
     * @param clientErrors respostas 4xx no período
     * @param availabilityPercent proporção de requisições que <b>não</b> responderam 5xx, com uma
     *     casa. É disponibilidade do que foi atendido, e não do que ficou de pé: o app parado não
     *     escreve linha nenhuma e por isso não pesa aqui
     * @param p95Ms teto da faixa em que o p95 do período inteiro cai; {@code -1} sem medição e
     *     {@code 0} quando ele cai acima da última faixa da escada
     * @param latencyCeilingMs o último degrau da escada de latência. Vai na resposta para que a
     *     tela saiba escrever "acima de X ms" sem repetir o número — a escada é do servidor, e
     *     duplicá-la no cliente é garantir que um dia elas discordem
     * @param startsInPeriod quantas vezes a aplicação subiu no período. Mais de uma sem deploy é a
     *     resposta para "ele reiniciou sozinho de madrugada?"
     */
    @Schema(description = "Requisições, erro e latência do próprio app — agregado, e de ninguém.")
    public record HealthView(
            int hours,
            Instant from,
            Instant to,
            Instant collectedThrough,
            long requests,
            long serverErrors,
            long clientErrors,
            double availabilityPercent,
            long p95Ms,
            long latencyCeilingMs,
            List<HealthPoint> hourly,
            List<RouteHealth> routes,
            List<RouteHealth> slowest,
            long startsInPeriod,
            List<StartView> starts) {}

    /**
     * Uma hora do período.
     *
     * <p>Todas as horas vêm, inclusive as sem requisição nenhuma: buraco na série viraria uma linha
     * reta ligando duas pontas distantes, que é o gráfico afirmando que nada aconteceu no meio.
     */
    public record HealthPoint(Instant hour, long requests, long serverErrors, long clientErrors) {}

    /**
     * Uma rota, somada no período.
     *
     * @param path o <b>padrão</b> da rota, nunca um caminho com segmento preenchido
     * @param errorPercent proporção de 5xx daquela rota, com uma casa
     * @param p95Ms teto da faixa em que o p95 daquela rota cai
     * @param avgMs média simples do período — publicada ao lado do p95 e não no lugar dele, porque
     *     é justamente a média que esconde a cauda que interessa
     */
    public record RouteHealth(
            String path,
            long requests,
            long serverErrors,
            double errorPercent,
            long p95Ms,
            long avgMs,
            long maxMs) {}

    /** Uma subida da aplicação. Dois campos, e nenhum deles é de alguém. */
    public record StartView(Instant startedAt, String profiles) {}
}
