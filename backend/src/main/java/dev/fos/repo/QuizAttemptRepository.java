package dev.fos.repo;

import dev.fos.model.QuizAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    /**
     * Histórico completo, em ordem cronológica.
     *
     * <p>Sem recorte por data de propósito: é a primeira aprovação — que pode ser bem anterior à
     * janela medida — que define se uma tentativa conta como quiz refeito. Ordena por id no desempate
     * porque a data é o dia, e duas tentativas do mesmo dia precisam manter a ordem em que ocorreram.
     */
    List<QuizAttempt> findByUserIdOrderByAttemptedOnAscIdAsc(Long userId);
}
