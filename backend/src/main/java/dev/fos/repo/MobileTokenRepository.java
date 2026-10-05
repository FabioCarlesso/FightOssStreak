package dev.fos.repo;

import dev.fos.model.MobileToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * As escritas em {@code mobile_token} são todas em massa, e é de propósito (#148).
 *
 * <p>O app dispara várias requisições ao abrir, todas com o mesmo token, e cada uma passa pelo
 * {@code BearerTokenFilter}. Apagar ou atualizar pela entidade carregada faz o Hibernate conferir
 * que a linha mudou: quando outra requisição já a apagou, o flush encontra zero linhas, lança
 * {@code StaleObjectStateException}, e a requisição que perdeu responde 500 ainda no filtro, sem
 * passar pelo {@code ApiExceptionHandler}. Isso foi reproduzido com um token vencido e trinta
 * requisições simultâneas. A escrita em massa não confere nada: apagar o que já sumiu apaga zero
 * linhas, e todas respondem o mesmo 401.
 *
 * <p>As três primeiras limpam o contexto ({@code clearAutomatically}), porque a entidade que o
 * {@code MobileTokens} acabou de carregar ficaria com o valor de antes da escrita: na mesma
 * transação, a próxima busca devolveria esse cache. Elas só rodam em transação própria (o filtro e
 * o "sair"), então não há mais nada no contexto a perder. A última não limpa: a revogação roda
 * dentro da transação da troca de senha, que ainda altera a identidade já carregada depois dela, e
 * limpar o contexto a desanexaria.
 */
public interface MobileTokenRepository extends JpaRepository<MobileToken, Long> {

    Optional<MobileToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from MobileToken t where t.id = :id")
    int deleteRow(Long id);

    /** O "sair" do app: token que já não existe não é erro. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("delete from MobileToken t where t.tokenHash = :tokenHash")
    int deleteRowByTokenHash(String tokenHash);

    /** O uso diário do token. A linha que sumiu no meio do caminho só não é atualizada. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update MobileToken t set t.lastUsedAt = :now where t.id = :id")
    int touch(Long id, Instant now);

    /**
     * Revoga todos os tokens de várias identidades de uma vez.
     *
     * <p>Por identidade, como a senha: a redefinição de senha e a exclusão da conta passam aqui as
     * identidades da conta, e assim o celular que entrou pelo Google também sai quando a senha
     * muda.
     */
    @Modifying(flushAutomatically = true)
    @Transactional
    @Query("delete from MobileToken t where t.identityId in :identityIds")
    int deleteByIdentityIdIn(List<Long> identityIds);
}
