package dev.fos.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Freio dos endpoints públicos de acesso por e-mail.
 *
 * <p>Eles são a única superfície do app que qualquer um alcança sem sessão, e uma delas dispara
 * e-mail. Sem freio, viram jeito de encher a fila do dono e de gastar o domínio do remetente.
 *
 * <p>Em memória de propósito: o app roda com uma réplica (D22), e um contador distribuído aqui
 * seria infra nova para resolver um problema que não existe nesta escala. Se um dia houver mais de
 * uma réplica, isto passa a ser um limite por réplica — o comentário fica como aviso.
 */
@Component
public class AccessRateLimiter {

    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    /** Verdadeiro quando a tentativa cabe na janela; falso quando o limite estourou. */
    public boolean tryAcquire(String key, int max, Duration window, Instant now) {
        Deque<Instant> recent = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            Instant limite = now.minus(window);
            while (!recent.isEmpty() && recent.peekFirst().isBefore(limite)) {
                recent.pollFirst();
            }
            if (recent.size() >= max) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }

    /** Some com chaves que não são tocadas há tempo, para o mapa não crescer sem fim. */
    public void evictOlderThan(Duration window, Instant now) {
        Instant limite = now.minus(window);
        hits.entrySet()
                .removeIf(
                        entry -> {
                            Deque<Instant> recent = entry.getValue();
                            synchronized (recent) {
                                return recent.isEmpty() || recent.peekLast().isBefore(limite);
                            }
                        });
    }
}
