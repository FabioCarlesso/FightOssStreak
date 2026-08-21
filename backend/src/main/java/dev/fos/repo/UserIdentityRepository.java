package dev.fos.repo;

import dev.fos.model.UserIdentity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserIdentityRepository extends JpaRepository<UserIdentity, Long> {

    /** A chave real da identidade — ver o javadoc de {@link UserIdentity}. */
    Optional<UserIdentity> findByProviderAndProviderSubject(
            String provider, String providerSubject);

    List<UserIdentity> findByUserId(Long userId);

    /**
     * Identidades com este e-mail, já verificado.
     *
     * <p>Só serve para achar a conta-modelo da demonstração por configuração (#62), e a exigência
     * de verificação é o que impede que digitar o endereço da conta-modelo na tela de pedido de
     * acesso — que grava identidade não verificada — passe a apontar a cópia para a conta errada.
     */
    List<UserIdentity> findByEmailIgnoreCaseAndEmailVerifiedTrue(String email);

    List<UserIdentity> findByUserIdIn(List<Long> userIds);

    void deleteByUserId(Long userId);
}
