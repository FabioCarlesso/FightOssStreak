package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O freio compartilhado, e a armadilha de compartilhá-lo.
 *
 * <p>São quatro consumidores no mesmo mapa, com janelas diferentes: 15 minutos para tentativa de
 * senha (#81), uma hora para e-mail e demonstração, dez minutos para a coleta de uso (#84). A
 * varredura existe para o mapa não crescer sem fim — e varrendo o mapa inteiro com a janela curta,
 * ela apaga o contador do vizinho de janela longa. Um freio apagado não avisa que parou de frear,
 * então quem garante que isso não acontece é este arquivo.
 */
class AccessRateLimiterTest {

    private static final Instant AGORA = Instant.parse("2026-08-28T12:00:00Z");
    private static final Duration DEZ_MINUTOS = Duration.ofMinutes(10);
    private static final Duration QUINZE_MINUTOS = Duration.ofMinutes(15);

    private final AccessRateLimiter freio = new AccessRateLimiter();

    @Test
    @DisplayName("o teto segura na chave certa e libera a próxima")
    void tetoPorChave() {
        assertThat(freio.tryAcquire("uso:a", 2, DEZ_MINUTOS, AGORA)).isTrue();
        assertThat(freio.tryAcquire("uso:a", 2, DEZ_MINUTOS, AGORA)).isTrue();
        assertThat(freio.tryAcquire("uso:a", 2, DEZ_MINUTOS, AGORA)).isFalse();

        // Chave diferente é orçamento diferente — é o que faz o freio ser por visita.
        assertThat(freio.tryAcquire("uso:b", 2, DEZ_MINUTOS, AGORA)).isTrue();
    }

    @Test
    @DisplayName("a varredura com prefixo não encosta no contador de força bruta de senha")
    void varreduraComPrefixoNaoApagaOVizinho() {
        // Alguém errou a senha há doze minutos: dentro da janela de 15, ainda deve contar.
        Instant errouASenha = AGORA.minus(Duration.ofMinutes(12));
        freio.record("tentativa:alguem@example.test", QUINZE_MINUTOS, errouASenha);
        freio.record("tentativa:alguem@example.test", QUINZE_MINUTOS, errouASenha);
        freio.record("tentativa:alguem@example.test", QUINZE_MINUTOS, errouASenha);

        // E uma visita da coleta, parada há doze minutos: essa saiu da janela de 10 e pode sumir.
        freio.record("uso:chave-velha", DEZ_MINUTOS, errouASenha);

        // A coleta varre com a janela DELA, que é a curta. É o que roda em toda navegação.
        freio.evictOlderThan("uso:", DEZ_MINUTOS, AGORA);

        assertThat(freio.isBlocked("tentativa:alguem@example.test", 3, QUINZE_MINUTOS, AGORA))
                .as("varrer as chaves da coleta não pode zerar o freio da senha")
                .isTrue();
        assertThat(freio.isBlocked("uso:chave-velha", 1, DEZ_MINUTOS, AGORA)).isFalse();
    }

    @Test
    @DisplayName("a varredura sem prefixo segue varrendo tudo — é o que os outros três usam")
    void varreduraSemPrefixoVarreTudo() {
        Instant antes = AGORA.minus(Duration.ofMinutes(12));
        freio.record("tentativa:alguem@example.test", QUINZE_MINUTOS, antes);
        freio.record("uso:chave", DEZ_MINUTOS, antes);

        freio.evictOlderThan(DEZ_MINUTOS, AGORA);

        assertThat(freio.isBlocked("tentativa:alguem@example.test", 1, QUINZE_MINUTOS, AGORA))
                .isFalse();
        assertThat(freio.isBlocked("uso:chave", 1, DEZ_MINUTOS, AGORA)).isFalse();
    }

    @Test
    @DisplayName("chave ainda quente não é varrida, nem com o prefixo dela")
    void oQueEstaNaJanelaFica() {
        freio.record("uso:quente", DEZ_MINUTOS, AGORA.minus(Duration.ofMinutes(2)));

        freio.evictOlderThan("uso:", DEZ_MINUTOS, AGORA);

        assertThat(freio.isBlocked("uso:quente", 1, DEZ_MINUTOS, AGORA)).isTrue();
    }
}
