package dev.fos.service;

import dev.fos.repo.StreakFreezeRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Grava o dia perdoado sem que perder uma corrida vire erro na tela (#99, D55).
 *
 * <p>Existe por causa de um defeito medido, não por precaução: {@code GET /api/streak}
 * <b>escreve</b> — é assim que o perdão é materializado sem job diário —, e duas requisições da
 * mesma conta chegam juntas o tempo todo, porque duas abas abertas bastam. As duas derivavam o
 * mesmo dia perdido, as duas tentavam inserir, e a que perdia morria na {@code uq_streak_freeze}:
 * 25 leituras simultâneas produziam de 3 a 15 respostas <b>500</b>. O estado final ficava certo —
 * uma linha —, e mesmo assim a pessoa via erro na home.
 *
 * <p>E era pior que uma tela feia: o 5xx era da <b>própria aplicação</b> e entrava na taxa que
 * dispara o alerta de incidente da D54. Duas abas abertas podiam mandar e-mail de "site fora do
 * ar".
 *
 * <p>Duas peças fecham isso, e nenhuma sozinha bastou. O {@code WHERE NOT EXISTS} do repositório
 * estreita a janela para dentro de uma instrução — medido, caiu de ~8% para ~2% das requisições, e
 * <b>não</b> chegou a zero, porque no {@code READ COMMITTED} do Postgres a subconsulta não enxerga
 * a inserção não confirmada do vizinho. Quem fecha é esta classe: transação <b>própria</b> ({@code
 * REQUIRES_NEW}), para que a violação suje só a inserção e não a transação de quem chamou, e a
 * violação engolida, porque quem perde a corrida não tem nada a fazer — o que ela queria gravar já
 * está gravado, com a mesma data e o mesmo significado.
 *
 * <p>É o que faz a restrição única ser <b>rede</b> e não mina, que é o que a D55 já dizia que ela
 * era.
 */
@Service
public class StreakFreezeWriter {

    private static final Logger log = LoggerFactory.getLogger(StreakFreezeWriter.class);

    private final StreakFreezeRepository freezes;
    private final TransactionTemplate emTransacaoPropria;

    public StreakFreezeWriter(StreakFreezeRepository freezes, PlatformTransactionManager tx) {
        this.freezes = freezes;
        this.emTransacaoPropria = new TransactionTemplate(tx);
        this.emTransacaoPropria.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Registra um dia perdoado. Repetir é seguro, e perder a corrida não é erro.
     *
     * <p>{@code REQUIRES_NEW} de propósito: {@link DrillService} chama o cálculo de streak dentro
     * da transação dele, e sem isolar a inserção uma violação aqui derrubaria o registro do drill —
     * trocaria um 500 na home por um 500 ao registrar treino, que é pior.
     *
     * <p>E é {@link TransactionTemplate} programático, não {@code @Transactional}: com a anotação,
     * capturar a violação <b>não bastava</b>, e isso foi medido. A tradução da exceção já marca a
     * transação como {@code rollback-only}, então o {@code catch} devolvia normalmente e era o
     * <b>commit</b> da transação interna que estourava depois, com {@code
     * UnexpectedRollbackException} — o mesmo 500, um passo adiante e mais difícil de ler. Aqui o
     * desfazimento é pedido explicitamente, e a transação é desfeita em vez de confirmada.
     */
    public void registrar(Long userId, LocalDate coveredOn, Instant quando) {
        emTransacaoPropria.execute(
                status -> {
                    try {
                        freezes.inserirSeAusente(userId, coveredOn, quando);
                    } catch (DataIntegrityViolationException outraRequisicaoChegouPrimeiro) {
                        // O dia já está gravado, que é exatamente o que esta chamada queria.
                        // DEBUG porque é corrida normal entre abas, não defeito: em WARN viraria
                        // ruído no log de algo que o app resolveu sozinho.
                        status.setRollbackOnly();
                        log.debug(
                                "Dia {} da conta {} já havia sido perdoado por outra requisição",
                                coveredOn,
                                userId);
                    }
                    return null;
                });
    }
}
