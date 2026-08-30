package dev.fos.repo;

import dev.fos.model.AppUser;
import dev.fos.model.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * As contas.
 *
 * <p>{@code JpaSpecificationExecutor} entrou com a listagem de administração (#89): são três
 * filtros opcionais mais busca, e escrever isso em JPQL exigiria ou uma consulta por combinação ou
 * um {@code :param IS NULL} por filtro — o segundo passa parâmetro tipado como nulo para o banco e
 * é onde enum costuma quebrar. A montagem em {@code Specification} deixa fora da consulta o filtro
 * que não veio.
 */
public interface AppUserRepository
        extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    /**
     * A conta dona de um e-mail verificado (#81).
     *
     * <p>É a consulta que faz identidade nova se anexar à conta que já existe, em vez de criar a
     * segunda conta vazia que a falta de merge por e-mail (D36) produziria com senha própria.
     */
    Optional<AppUser> findByPrimaryEmail(String primaryEmail);

    /** Demonstrações vencidas — a varredura preguiçosa da criação (#62) come daqui. */
    List<AppUser> findByDemoExpiresAtLessThan(Instant instant);

    /** Demonstrações vivas, para o teto de simultâneas. */
    long countByDemoExpiresAtIsNotNull();

    /**
     * Quantas contas de gente de verdade existem — o "total" do painel (#85).
     *
     * <p>Demonstração fora, pelo mesmo motivo da guarda do último {@code ADMIN}: ela não é de
     * ninguém e vence em duas horas (D39), e contá-la faria o painel dizer que o app tem contas que
     * ele não tem.
     */
    long countByDemoExpiresAtIsNull();

    /** As que nasceram dentro do período do painel (#85). Fim exclusivo. */
    long countByDemoExpiresAtIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Instant from, Instant to);

    /**
     * Quantas contas de gente de verdade têm este papel.
     *
     * <p>É a guarda do último {@code ADMIN} (#89, #90): o app não pode ficar sem ninguém que
     * administre, porque não há tela para consertar isso de dentro — só redeploy com {@code
     * FOS_OWNER_EMAILS}. Demonstração fica de fora da conta porque ela nunca administra (D39), e
     * contá-la faria o app se achar coberto por uma conta que vence em duas horas.
     */
    long countByRoleAndDemoExpiresAtIsNull(Role role);
}
