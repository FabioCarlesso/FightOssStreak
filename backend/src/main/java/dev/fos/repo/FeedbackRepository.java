package dev.fos.repo;

import dev.fos.model.Feedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /** Fila do dono, da mais antiga para a mais nova — mesmo critério da fila de acesso. */
    List<Feedback> findAllByOrderByCreatedAtAsc();

    /**
     * O que {@code DELETE /api/me} apaga do autor (FOS-04, D64).
     *
     * <p>Apagar, e não anonimizar: a mensagem é texto livre de quem escreveu, e {@code user_id} é
     * {@code NOT NULL} justamente porque feedback sem autor não tem a quem responder.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Feedback f where f.userId = :userId")
    int deleteByUserId(Long userId);

    /**
     * Esquece quem decidiu, quando quem decidiu exclui a conta (FOS-04, D64).
     *
     * <p>O feedback é de outra pessoa e continua na fila; só a referência à conta que sumiu sai. Ao
     * contrário de {@code app_user.decided_by}, esta coluna tem chave estrangeira (V10) — deixá-la
     * apontando para a conta faria a exclusão bater na FK e responder 500.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Feedback f set f.decidedBy = null where f.decidedBy = :userId")
    int clearDecidedBy(Long userId);
}
