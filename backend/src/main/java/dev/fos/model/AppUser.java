package dev.fos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Conta da aplicação.
 *
 * <p>Desde a D47 ela nasce {@link AccessStatus#APROVADO} por qualquer caminho: cadastro com senha,
 * login por provedor ou demonstração. A fila de aprovação que a D36 criou saiu inteira na D48 —
 * quando qualquer um pode criar conta, aprovação não filtra ninguém, só atrasa.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String label;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status", nullable = false)
    private AccessStatus accessStatus;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "demo_expires_at")
    private Instant demoExpiresAt;

    /**
     * O e-mail VERIFICADO que identifica esta conta, e o único vínculo entre provedores (#81).
     *
     * <p>A D36 fixou que a chave da identidade é {@code (provider, subject)} e que e-mail nunca
     * funde contas — a Apple entrega relay, o Facebook pode não devolver endereço nenhum. Com senha
     * própria essa regra passou a produzir o defeito que ela evitava: quem entrou pelo Google e
     * depois se cadastrou com o mesmo endereço ganharia uma segunda conta vazia. Esta coluna é a
     * exceção mínima — não funde progresso de contas já usadas, só diz de quem é o endereço para
     * que a identidade nova se anexe à conta certa.
     *
     * <p>Nulo em conta de demonstração, em conta de provedor sem e-mail verificado e em cadastro
     * ainda não confirmado. Endereço não verificado nunca chega aqui: se chegasse, digitar o
     * endereço de outra pessoa no cadastro daria acesso à conta dela.
     */
    @Column(name = "primary_email")
    private String primaryEmail;

    protected AppUser() {
        // JPA
    }

    private AppUser(String label, Instant createdAt, AccessStatus status, Instant decidedAt) {
        this.label = label;
        this.createdAt = createdAt;
        this.accessStatus = status;
        this.requestedAt = createdAt;
        this.decidedAt = decidedAt;
    }

    /** Conta do dono (e-mail verificado em {@code fos.auth.owner-emails}): já entra liberada. */
    public static AppUser approved(String label, Instant now) {
        return new AppUser(label, now, AccessStatus.APROVADO, now);
    }

    /**
     * Conta criada por cadastro com senha (#81): nasce liberada, e ainda não verificada.
     *
     * <p>{@code APROVADO} sem fila é a decisão da D47: quando qualquer um pode criar conta, a fila
     * não filtra ninguém — só atrasa. O que segura o cadastro não é a aprovação do autor, é o
     * e-mail: a conta nasce sem sessão e sem {@code primaryEmail}, e só existe de verdade depois
     * que o link de confirmação for aberto.
     */
    public static AppUser forPassword(String label, Instant now) {
        return new AppUser(label, now, AccessStatus.APROVADO, now);
    }

    /**
     * Conta de demonstração: nasce liberada e já com prazo para morrer (#62).
     *
     * <p>Nascer {@code APROVADO} sem aprovação nenhuma contraria a letra da D37, e é exceção
     * consciente: o que a sustenta é o prazo — mais a conta não ter identidade de ninguém, não ser
     * dona de nada e ser apagada por inteiro quando vence.
     */
    public static AppUser demo(String label, Instant now, Instant expiresAt) {
        AppUser user = new AppUser(label, now, AccessStatus.APROVADO, now);
        user.demoExpiresAt = expiresAt;
        return user;
    }

    /**
     * Libera a conta.
     *
     * <p>Sobrou de uma época em que havia o que liberar. Continua sendo chamado no bootstrap do
     * dono, onde a conta semeada pela V2 é adotada — ali ele é idempotente e só carimba a data.
     */
    public void approve(Instant now) {
        this.accessStatus = AccessStatus.APROVADO;
        this.decidedAt = now;
    }

    /**
     * Declara esta conta dona de um endereço verificado.
     *
     * <p>Chamado quando um link de confirmação é aberto, ou quando um provedor devolve e-mail
     * verificado. Só depois disso o endereço vincula identidade nova — ver {@link #primaryEmail}.
     */
    public void claimPrimaryEmail(String email) {
        this.primaryEmail = email;
    }

    /** Dá nome à linha semeada pela V2, que nasceu como {@code usuario-local}. */
    public void rename(String label) {
        this.label = label;
    }

    public boolean isApproved() {
        return accessStatus == AccessStatus.APROVADO;
    }

    /** Conta descartável de demonstração — nunca uma conta de gente de verdade. */
    public boolean isDemo() {
        return demoExpiresAt != null;
    }

    /**
     * Demonstração cujo prazo passou.
     *
     * <p>Quem responde com isto trata a sessão como inexistente (401), e não como acesso negado: a
     * conta ainda está no banco só porque a varredura é preguiçosa, e para quem está do outro lado
     * ela já não existe.
     */
    public boolean isDemoExpired(Instant now) {
        return demoExpiresAt != null && !now.isBefore(demoExpiresAt);
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AccessStatus getAccessStatus() {
        return accessStatus;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getDemoExpiresAt() {
        return demoExpiresAt;
    }

    public String getPrimaryEmail() {
        return primaryEmail;
    }
}
