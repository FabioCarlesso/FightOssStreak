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
 * <p>Eles são a única superfície do app que qualquer um alcança sem sessão, e várias delas disparam
 * e-mail. Sem freio, viram jeito de encher a fila do dono e de gastar o domínio do remetente.
 *
 * <p>Desde a #81 ele também segura força bruta de senha, e é por isso que "consultar" e "registrar"
 * viraram operações separadas: ali o que se conta é tentativa errada, não requisição.
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
            if (expire(recent, window, now) >= max) {
                return false;
            }
            recent.addLast(now);
            return true;
        }
    }

    /**
     * Só consulta: o limite estourou?
     *
     * <p>Separado do {@link #tryAcquire} porque o freio de login (#81) conta <b>tentativa
     * errada</b> e não requisição. Contar o acerto junto derrubaria quem entra e sai várias vezes
     * no mesmo dia, sem encarecer nada para quem ataca.
     */
    public boolean isBlocked(String key, int max, Duration window, Instant now) {
        Deque<Instant> recent = hits.get(key);
        if (recent == null) {
            return false;
        }
        synchronized (recent) {
            return expire(recent, window, now) >= max;
        }
    }

    /** Só registra — a metade complementar do {@link #isBlocked}. */
    public void record(String key, Duration window, Instant now) {
        Deque<Instant> recent = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (recent) {
            expire(recent, window, now);
            recent.addLast(now);
        }
    }

    /** Zera a chave. É o que o acerto de senha faz com o contador de erros daquele e-mail. */
    public void clear(String key) {
        hits.remove(key);
    }

    /** Descarta o que saiu da janela e devolve quantos sobraram. */
    private static int expire(Deque<Instant> recent, Duration window, Instant now) {
        Instant limite = now.minus(window);
        while (!recent.isEmpty() && recent.peekFirst().isBefore(limite)) {
            recent.pollFirst();
        }
        return recent.size();
    }

    /**
     * Some com chaves que não são tocadas há tempo, para o mapa não crescer sem fim.
     *
     * <p><b>Varre o mapa inteiro</b>, e é por isso que existe a variante com prefixo abaixo: são
     * quatro consumidores com janelas diferentes (15 min para tentativa de senha, uma hora para
     * e-mail e demonstração, dez minutos para a coleta de uso) dividindo este mapa. Quem varre com
     * a janela curta apaga o contador do vizinho de janela longa — e um freio apagado não avisa que
     * parou de frear.
     */
    public void evictOlderThan(Duration window, Instant now) {
        evictOlderThan("", window, now);
    }

    /**
     * O mesmo, restrito às chaves de um prefixo.
     *
     * <p>É o que a coleta de uso (#84) usa. Ela é o único consumidor que roda em <b>toda
     * navegação</b> de qualquer visitante, e com a janela de dez minutos dela: varrendo o mapa
     * inteiro, um acesso anônimo qualquer zeraria o contador de força bruta de senha (#81) de quem
     * errou há mais de dez minutos, e o freio da senha viraria enfeite. Com o prefixo, cada
     * consumidor limpa a própria sujeira e não a do outro.
     */
    public void evictOlderThan(String prefixo, Duration window, Instant now) {
        Instant limite = now.minus(window);
        hits.entrySet()
                .removeIf(
                        entry -> {
                            if (!entry.getKey().startsWith(prefixo)) {
                                return false;
                            }
                            Deque<Instant> recent = entry.getValue();
                            synchronized (recent) {
                                return recent.isEmpty() || recent.peekLast().isBefore(limite);
                            }
                        });
    }
}
