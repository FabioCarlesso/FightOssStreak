package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A escada de latência e o percentil que sai dela (#86).
 *
 * <p>Teste puro, sem contexto do Spring: a razão de o histograma existir é aritmética — percentil
 * não é somável, e é o histograma que faz o p95 do período ser o mesmo cálculo do p95 de uma hora.
 * Se esta conta estiver errada, o painel publica um número que parece plausível e não é.
 */
class HttpStatsTest {

    @Test
    @DisplayName("cada duração cai na faixa cujo teto ela não ultrapassa")
    void eachDurationLandsOnItsBucket() {
        assertThat(HttpStats.faixa(0)).isZero();
        assertThat(HttpStats.faixa(25)).isZero();
        assertThat(HttpStats.faixa(26)).isEqualTo(1);
        assertThat(HttpStats.faixa(2500)).isEqualTo(HttpStats.ESCADA.length - 1);
        // A faixa de cima não tem teto — é ela que impede o histograma de mentir sobre a cauda.
        assertThat(HttpStats.faixa(9_999)).isEqualTo(HttpStats.ESCADA.length);
    }

    @Test
    @DisplayName("o p95 é o teto da faixa em que a 95ª centésima parte cai")
    void theP95IsTheCeilingOfItsBucket() {
        // 100 amostras: 96 rápidas e 4 lentas. A 95ª está entre as rápidas.
        long[] histograma = new long[HttpStats.FAIXAS];
        histograma[0] = 96;
        histograma[5] = 4;
        assertThat(HttpStats.percentil(histograma, 95)).isEqualTo(25);

        // Com 90 rápidas e 10 lentas, a 95ª já é lenta — e o p95 sobe de faixa.
        histograma[0] = 90;
        histograma[5] = 10;
        assertThat(HttpStats.percentil(histograma, 95)).isEqualTo(1000);
    }

    @Test
    @DisplayName("o p95 na faixa sem teto vem como zero, e sem amostra nenhuma vem como -1")
    void theOpenBucketAndTheAbsenceOfSamplesAreDifferentAnswers() {
        long[] acima = new long[HttpStats.FAIXAS];
        acima[HttpStats.FAIXAS - 1] = 3;
        assertThat(HttpStats.percentil(acima, 95)).isZero();
        assertThat(HttpStats.rotulo(0)).isEqualTo("acima de 2500 ms");

        // Zero e "não medimos" precisam ser distinguíveis: são conclusões opostas para quem lê.
        assertThat(HttpStats.percentil(new long[HttpStats.FAIXAS], 95)).isEqualTo(-1);
        assertThat(HttpStats.rotulo(-1)).isEqualTo("sem medição");
    }

    @Test
    @DisplayName("a posição do percentil é arredondada para cima, e não para baixo")
    void thePercentileRankRoundsUp() {
        // 10 amostras, uma lenta: a 95ª centésima parte é a décima amostra (teto de 9,5), então a
        // lenta manda. Arredondando para baixo seria a nona, e a cauda sumiria do número.
        long[] histograma = new long[HttpStats.FAIXAS];
        histograma[0] = 9;
        histograma[4] = 1;
        assertThat(HttpStats.percentil(histograma, 95)).isEqualTo(500);

        // Com 20 amostras a posição é a 19ª — uma lenta só já não move o p95, e é isso que o
        // percentil existe para fazer: ignorar o caso isolado sem ignorar a cauda.
        histograma[0] = 19;
        histograma[4] = 1;
        assertThat(HttpStats.percentil(histograma, 95)).isEqualTo(25);
    }
}
