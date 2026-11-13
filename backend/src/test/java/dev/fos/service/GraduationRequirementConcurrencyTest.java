package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.repo.GraduationRequirementRepository;
import dev.fos.web.dto.RequirementDtos;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * O teto da checklist sob importações simultâneas (#196, D78).
 *
 * <p>Sem {@code @Transactional} de propósito: cada thread precisa da própria transação, que é onde
 * a corrida acontecia — as duas liam o mesmo total, passavam juntas do teto e repetiam o {@code
 * order_index}. O que a impede é a trava da conta em {@code importText}.
 */
@SpringBootTest
@ActiveProfiles("test")
class GraduationRequirementConcurrencyTest {

    private static final long SEEDED_USER_ID = 1L;
    private static final int IMPORTS = 4;
    private static final int LINES_EACH = 40;

    @Autowired private GraduationRequirementService service;
    @Autowired private GraduationRequirementRepository requisitos;

    @AfterEach
    void cleanUp() {
        service.deleteAll(SEEDED_USER_ID);
    }

    @Test
    @DisplayName("importações simultâneas nunca passam do teto nem repetem a ordem")
    void concurrentImportsRespectTheCap() throws Exception {
        String texto =
                IntStream.rangeClosed(1, LINES_EACH)
                        .mapToObj(i -> "Requisito fictício " + i)
                        .collect(Collectors.joining("\n"));
        CountDownLatch largada = new CountDownLatch(1);
        Callable<Boolean> importar =
                () -> {
                    largada.await();
                    try {
                        service.importText(
                                SEEDED_USER_ID, new RequirementDtos.ImportRequest(null, texto));
                        return true;
                    } catch (IllegalArgumentException acimaDoTeto) {
                        return false;
                    }
                };

        ExecutorService pool = Executors.newFixedThreadPool(IMPORTS);
        try {
            List<Future<Boolean>> resultados =
                    IntStream.range(0, IMPORTS).mapToObj(i -> pool.submit(importar)).toList();
            largada.countDown();
            long aceitas = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get()) {
                    aceitas++;
                }
            }

            assertThat(aceitas).as("40 + 40 passa de 60: só uma cabe").isEqualTo(1);
            List<Integer> ordens =
                    requisitos.findByUserIdOrderByOrderIndexAscIdAsc(SEEDED_USER_ID).stream()
                            .map(r -> r.getOrderIndex())
                            .toList();
            assertThat(ordens).hasSize(LINES_EACH).doesNotHaveDuplicates();
        } finally {
            pool.shutdownNow();
        }
    }
}
