package dev.fos.service;

/**
 * A escada de latência — fixa, e é isso que a torna somável (#86).
 *
 * <p><b>Por que histograma e não p95 guardado por hora.</b> Percentil não é somável: o p95 de um
 * período não sai da média nem do máximo dos p95 de cada hora, e as duas contas dão números que
 * parecem certos e não são. Guardando a contagem por faixa, o p95 de qualquer recorte — uma hora,
 * uma semana, uma rota, todas as rotas — é a mesma varredura da escada, e ela devolve <b>o teto da
 * faixa em que o percentil cai</b>: "p95 até 250 ms" é uma afirmação verdadeira, e "p95 = 237 ms"
 * seria uma precisão que este armazenamento não tem.
 *
 * <p><b>Por que fixa.</b> A escada é do programa, não do dado. Somar contagens gravadas com duas
 * escadas diferentes daria um percentil inventado, então trocá-la é migration — ao contrário das
 * dimensões da coleta de uso (#84), onde valor novo é linha nova de propósito.
 *
 * <p>Nada aqui guarda amostra: só quantas requisições caíram em cada faixa. Uma requisição não é
 * ligável a outra, e nenhuma delas é ligável a alguém.
 */
public final class HttpStats {

    /**
     * Os tetos de cada faixa, em milissegundos. A última faixa é "acima de tudo isso" e não tem
     * teto — é ela que impede o histograma de mentir sobre a cauda.
     */
    public static final long[] ESCADA = {25, 50, 100, 250, 500, 1000, 2500};

    /** Quantas faixas o histograma tem: a escada mais a de cima. */
    public static final int FAIXAS = ESCADA.length + 1;

    /** O percentil que o painel publica. */
    public static final int PERCENTIL = 95;

    private HttpStats() {}

    /** Em qual faixa uma duração cai. */
    public static int faixa(long millis) {
        for (int i = 0; i < ESCADA.length; i++) {
            if (millis <= ESCADA[i]) {
                return i;
            }
        }
        return ESCADA.length;
    }

    /**
     * O teto da faixa em que o percentil cai, ou {@code -1} quando não há amostra nenhuma.
     *
     * <p>Cair na faixa de cima devolve {@code 0}: não há teto a informar, e quem lê precisa
     * distinguir "acima de 2500 ms" de "não medimos". Ver {@link #rotulo(long)}.
     */
    public static long percentil(long[] histograma, int percentil) {
        long total = 0;
        for (long faixa : histograma) {
            total += faixa;
        }
        if (total == 0) {
            return -1;
        }
        // Teto, e não arredondamento: com 20 amostras o p95 é a vigésima, não a décima nona.
        long alvo = (long) Math.ceil(total * percentil / 100.0);
        long acumulado = 0;
        for (int i = 0; i < histograma.length; i++) {
            acumulado += histograma[i];
            if (acumulado >= alvo) {
                return i < ESCADA.length ? ESCADA[i] : 0;
            }
        }
        return 0;
    }

    /** Como o teto de uma faixa se lê: {@code 0} é a faixa sem teto, {@code -1} é sem amostra. */
    public static String rotulo(long teto) {
        if (teto < 0) {
            return "sem medição";
        }
        return teto == 0 ? "acima de " + ESCADA[ESCADA.length - 1] + " ms" : "até " + teto + " ms";
    }
}
