package dev.fos.repo;

import dev.fos.model.LoginToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface LoginTokenRepository extends JpaRepository<LoginToken, Long> {

    Optional<LoginToken> findByTokenHash(String tokenHash);

    /**
     * Tokens da conta que ainda não foram usados — de qualquer propósito.
     *
     * <p>Serve para a redefinição de senha queimar o que estiver pendente: link antigo na caixa de
     * entrada continuaria valendo depois de a senha ter mudado, que é a janela que um invasor com
     * acesso à caixa usaria.
     */
    List<LoginToken> findByUserIdAndUsedAtIsNull(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
