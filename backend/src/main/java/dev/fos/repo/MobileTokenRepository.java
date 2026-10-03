package dev.fos.repo;

import dev.fos.model.MobileToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface MobileTokenRepository extends JpaRepository<MobileToken, Long> {

    Optional<MobileToken> findByTokenHash(String tokenHash);

    /**
     * Revoga todos os tokens de várias identidades de uma vez.
     *
     * <p>Por identidade, como a senha: a redefinição de senha e a exclusão da conta passam aqui as
     * identidades da conta, e assim o celular que entrou pelo Google também sai quando a senha
     * muda.
     */
    @Transactional
    void deleteByIdentityIdIn(List<Long> identityIds);
}
