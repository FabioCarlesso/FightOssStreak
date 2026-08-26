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

    /**
     * O que esta conta pode fazer no app — dado, e não mais configuração (D49).
     *
     * <p>Até a D48 o papel saía de {@code fos.auth.owner-emails} a cada requisição, e administrador
     * novo exigia deploy. A lista continua existindo e virou <b>semente</b>: na subida ela promove
     * quem ainda é {@code USUARIO}, e é a saída de emergência de um ambiente que ficou sem nenhum
     * administrador. Quem decide o papel continua sendo um lugar só, {@code AccountService.roleOf}
     * — o que mudou foi a fonte, não o ponto.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Column(name = "role_changed_at")
    private Instant roleChangedAt;

    @Column(name = "role_changed_by")
    private Long roleChangedBy;

    /**
     * Quem decidiu o estado de acesso atual, e por quê (#90).
     *
     * <p>Guarda o id de quem bloqueou ou desbloqueou, sem FK: quem decidiu pode excluir a própria
     * conta depois, e a trilha não pode sumir junto nem impedir a exclusão. Gravados nas duas
     * direções — desbloquear também é decisão de alguém.
     */
    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_reason")
    private String decidedReason;

    protected AppUser() {
        // JPA
    }

    private AppUser(String label, Instant createdAt, AccessStatus status, Instant decidedAt) {
        this.label = label;
        this.createdAt = createdAt;
        this.accessStatus = status;
        this.requestedAt = createdAt;
        this.decidedAt = decidedAt;
        // Toda conta nasce comum, inclusive a do administrador: quem promove é a semente da
        // subida ou outro administrador, e as duas exigem e-mail verificado. Nascer ADMIN por
        // caminho nenhum é o que impede que o rótulo do login decida quem administra o app.
        this.role = Role.USUARIO;
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

    /**
     * Promove ou rebaixa, com a trilha de quem decidiu (#89).
     *
     * <p>As guardas — e-mail verificado, ninguém se rebaixa, nunca zero administradores — moram no
     * serviço, e não aqui: são regras sobre o <em>conjunto</em> de contas, e a entidade só conhece
     * a si mesma.
     */
    public void changeRole(Role role, Long decidedBy, Instant now) {
        this.role = role;
        this.roleChangedAt = now;
        this.roleChangedBy = decidedBy;
    }

    /**
     * Bloqueia ou desbloqueia a conta (#90).
     *
     * <p>É o produtor que faltava para {@code RECUSADO} desde a D48: o estado e o portão que o lê
     * já existiam, de propósito, esperando por isto. Derrubar as sessões abertas é responsabilidade
     * de quem chama — a entidade não sabe o que é sessão.
     */
    public void decideAccess(AccessStatus status, Long decidedBy, String reason, Instant now) {
        this.accessStatus = status;
        this.decidedAt = now;
        this.decidedBy = decidedBy;
        this.decidedReason = reason;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * A conta é dona de um e-mail verificado?
     *
     * <p>É {@code primary_email} que responde, e ele só é preenchido com endereço verificado (D47)
     * — por provedor externo ou pela confirmação do próprio app. Cadastro criado e nunca confirmado
     * responde falso, que é o ponto: sem isto, digitar o endereço de outra pessoa no cadastro e ser
     * promovido daria acesso de administração a quem nunca provou o endereço.
     */
    public boolean hasVerifiedEmail() {
        return primaryEmail != null;
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

    public Role getRole() {
        return role;
    }

    public Instant getRoleChangedAt() {
        return roleChangedAt;
    }

    public Long getRoleChangedBy() {
        return roleChangedBy;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public String getDecidedReason() {
        return decidedReason;
    }
}
