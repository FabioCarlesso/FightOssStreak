package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A chave de visita conta pessoas sem identificar ninguém (#84, D50).
 *
 * <p>O que estes testes protegem é uma promessa escrita em {@code docs/11-privacidade.md}: a mesma
 * pessoa em dois dias diferentes não é ligável, e nem a própria aplicação consegue recomputar a
 * chave de um evento passado. Se alguém "otimizar" o sal para ser fixo ou persistido, é aqui que
 * quebra.
 */
class VisitKeyTest {

    /** Relógio que anda quando o teste manda — o mesmo truque do resto do projeto, com data. */
    private static final class RelogioMovel extends Clock {
        private Instant agora;

        RelogioMovel(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration quanto) {
            agora = agora.plus(quanto);
        }

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
    }

    @Test
    @DisplayName("mesmo IP e mesmo navegador, no mesmo dia, dão a mesma chave")
    void sameDaySameKey() {
        VisitKey chaves = new VisitKey(new RelogioMovel(Instant.parse("2026-08-27T09:00:00Z")));

        String primeira = chaves.of("203.0.113.7", "Mozilla/5.0");
        String segunda = chaves.of("203.0.113.7", "Mozilla/5.0");

        assertThat(primeira).isEqualTo(segunda).hasSize(64);
    }

    @Test
    @DisplayName("IP diferente, ou navegador diferente, dá chave diferente")
    void differentInputsDifferentKeys() {
        VisitKey chaves = new VisitKey(new RelogioMovel(Instant.parse("2026-08-27T09:00:00Z")));

        String base = chaves.of("203.0.113.7", "Mozilla/5.0");

        assertThat(chaves.of("203.0.113.8", "Mozilla/5.0")).isNotEqualTo(base);
        assertThat(chaves.of("203.0.113.7", "Mozilla/5.0 (Android)")).isNotEqualTo(base);
    }

    @Test
    @DisplayName("virou o dia, virou o sal: a mesma pessoa não é ligável entre dois dias")
    void saltRotatesDaily() {
        RelogioMovel relogio = new RelogioMovel(Instant.parse("2026-08-27T23:59:00Z"));
        VisitKey chaves = new VisitKey(relogio);

        String ontem = chaves.of("203.0.113.7", "Mozilla/5.0");
        relogio.avancar(Duration.ofMinutes(2));
        String hoje = chaves.of("203.0.113.7", "Mozilla/5.0");

        assertThat(hoje).isNotEqualTo(ontem);
    }

    @Test
    @DisplayName("reiniciar a aplicação não permite recomputar a chave de um evento passado")
    void restartCannotRecomputeOldKeys() {
        Instant momento = Instant.parse("2026-08-27T09:00:00Z");

        // Duas instâncias no MESMO dia representam duas subidas da aplicação: o sal é sorteado na
        // memória de cada uma e nunca é gravado, então nem o mesmo dia salva.
        String antes = new VisitKey(new RelogioMovel(momento)).of("203.0.113.7", "Mozilla/5.0");
        String depois = new VisitKey(new RelogioMovel(momento)).of("203.0.113.7", "Mozilla/5.0");

        assertThat(depois).isNotEqualTo(antes);
    }

    @Test
    @DisplayName("entrada ausente não estoura — requisição sem User-Agent é comum")
    void nullInputsAreFine() {
        VisitKey chaves = new VisitKey(new RelogioMovel(Instant.parse("2026-08-27T09:00:00Z")));

        assertThat(chaves.of(null, null)).hasSize(64);
    }
}
