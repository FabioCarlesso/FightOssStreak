package dev.fos.service;

/**
 * Finalizador do splitmix64: espalha os bits de uma chave pequena e sequencial em algo bem
 * distribuído.
 *
 * <p>Extraído de {@code CurriculumQueryService.shuffleOptions} (D16) para ser reaproveitado por
 * {@link QuizRotation} — os dois problemas são o mesmo: transformar uma chave previsível numa ordem
 * que não vaza a posição de autoria.
 *
 * <p>Aqui estava o bug que fazia o embaralhamento de D16 não embaralhar: a versão anterior usava
 * {@code Long.hashCode}, que para qualquer valor abaixo de 2³¹ devolve o próprio valor — e as
 * chaves reais não passavam de ~91 milhões. A ordenação virava id crescente, ou seja, a ordem do
 * JSON, com a correta em primeiro lugar. Um multiplicador só não resolveria: multiplicação preserva
 * a ordem dos bits altos, e é justamente deles que a comparação depende.
 */
final class SplitMix {

    private SplitMix() {}

    static long finalize(long key) {
        long z = key;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
