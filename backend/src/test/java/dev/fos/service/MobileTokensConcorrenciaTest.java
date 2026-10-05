package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.UserIdentity;
import dev.fos.repo.MobileTokenRepository;
import dev.fos.repo.UserIdentityRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * O mesmo token em várias requisições simultâneas (#148).
 *
 * <p>É o que o app faz ao abrir: dispara {@code /me}, {@code /streak} e o resto de uma vez, todas
 * com o mesmo token. Com o token vencido, cada uma tentava apagar a mesma linha pela entidade, e a
 * que perdia a corrida respondia 500 com {@code ObjectOptimisticLockingFailureException}. Isso foi
 * reproduzido no Compose com trinta requisições.
 *
 * <p><b>Sem {@code @Transactional} na classe, de propósito:</b> a corrida só existe entre
 * transações diferentes, e por isso cada thread precisa abrir a própria. A limpeza é explícita.
 */
@SpringBootTest
@ActiveProfiles("test")
class MobileTokensConcorrenciaTest {

    private static final int THREADS = 16;
    private static final int RODADAS = 5;

    @Autowired private MobileTokens tokens;
    @Autowired private MobileTokenRepository tokenRows;
    @Autowired private AccountService accounts;
    @Autowired private UserIdentityRepository identities;
    @Autowired private JdbcTemplate jdbc;

    private UserIdentity identity;
    private ExecutorService pool;

    @BeforeEach
    void criarConta() {
        accounts.registerLogin(
                "google", "concorrencia", "concorrencia@example.test", true, "Concorrência");
        identity =
                identities.findByProviderAndProviderSubject("google", "concorrencia").orElseThrow();
        pool = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void limpar() throws InterruptedException {
        pool.shutdownNow();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        accounts.delete(identity.getUserId());
    }

    @Test
    @DisplayName("token vencido em requisições simultâneas: todas recusam, nenhuma estoura")
    void anExpiredTokenUsedConcurrentlyNeverThrows() throws Exception {
        for (int rodada = 0; rodada < RODADAS; rodada++) {
            String raw = tokens.issue(identity);
            envelhecer(Duration.ofDays(91));

            List<Optional<Long>> respostas = emParalelo(() -> tokens.authenticate(raw));

            assertThat(respostas).allMatch(Optional::isEmpty);
            assertThat(tokenRows.count()).isZero();
        }
    }

    @Test
    @DisplayName("sair no meio das requisições que gravam o uso: nenhuma estoura")
    void signingOutWhileTheTokenIsTouchedNeverThrows() throws Exception {
        for (int rodada = 0; rodada < RODADAS; rodada++) {
            String raw = tokens.issue(identity);
            // Dois dias sem uso: toda requisição tenta gravar o uso de novo.
            envelhecer(Duration.ofDays(2));

            emParalelo(
                    () -> {
                        tokens.revoke(raw);
                        return Optional.empty();
                    },
                    () -> tokens.authenticate(raw));

            assertThat(tokens.authenticate(raw)).isEmpty();
        }
    }

    /** Recua o último uso direto no banco: o relógio da aplicação é o do sistema aqui. */
    private void envelhecer(Duration quanto) {
        jdbc.update(
                "update mobile_token set last_used_at = ?",
                java.sql.Timestamp.from(Instant.now().minus(quanto)));
    }

    private List<Optional<Long>> emParalelo(Callable<Optional<Long>> tarefa) throws Exception {
        return emParalelo(tarefa, tarefa);
    }

    /**
     * A primeira tarefa roda numa thread e a segunda nas outras, todas soltas juntas pela barreira.
     * Exceção em qualquer uma reprova o teste, com a causa: é o 500 que esta classe procura.
     */
    private List<Optional<Long>> emParalelo(
            Callable<Optional<Long>> primeira, Callable<Optional<Long>> demais) throws Exception {
        CyclicBarrier largada = new CyclicBarrier(THREADS);
        List<Future<Optional<Long>>> futuros = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Callable<Optional<Long>> tarefa = i == 0 ? primeira : demais;
            futuros.add(
                    pool.submit(
                            () -> {
                                largada.await(5, TimeUnit.SECONDS);
                                return tarefa.call();
                            }));
        }
        List<Optional<Long>> respostas = new ArrayList<>();
        for (Future<Optional<Long>> futuro : futuros) {
            respostas.add(futuro.get(10, TimeUnit.SECONDS));
        }
        return respostas;
    }
}
