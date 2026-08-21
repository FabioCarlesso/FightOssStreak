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
 * <p>Desde a #24 a conta é criada por um login social bem-sucedido e nasce {@link
 * AccessStatus#PENDENTE}: autenticar não é entrar. Quem libera é o autor, pela fila de
 * solicitações.
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

    /**
     * Quando esta conta pendente entrou num resumo já enviado ao dono (#54).
     *
     * <p>Nulo é "ainda não anunciada", e é o que faz a janela da hora ter ou não novidade. Fica
     * aqui, junto do pedido, em vez de num relógio global: assim o estado do aviso sobrevive a
     * reinício e a deploy sem depender de o agendador ter rodado.
     */
    @Column(name = "queue_notice_sent_at")
    private Instant queueNoticeSentAt;

    /**
     * Prazo de vida de uma conta de demonstração (#62); nulo em toda conta de gente de verdade.
     *
     * <p>É a conta que expira, não uma sessão dela: passado o prazo não sobra progresso, identidade
     * nem sessão para guardar. Enquanto vale, a conta é comum em tudo o mais — grava de verdade, e
     * o que a distingue é ser descartável, não ser limitada.
     */
    @Column(name = "demo_expires_at")
    private Instant demoExpiresAt;

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

    /** Conta recém-criada por um login: entra na fila. */
    public static AppUser pending(String label, Instant now) {
        return new AppUser(label, now, AccessStatus.PENDENTE, null);
    }

    /** Conta do dono (e-mail verificado em {@code fos.auth.owner-emails}): já entra liberada. */
    public static AppUser approved(String label, Instant now) {
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

    public void approve(Instant now) {
        this.accessStatus = AccessStatus.APROVADO;
        this.decidedAt = now;
    }

    public void deny(Instant now) {
        this.accessStatus = AccessStatus.RECUSADO;
        this.decidedAt = now;
    }

    /** Dá nome à linha semeada pela V2, que nasceu como {@code usuario-local}. */
    public void rename(String label) {
        this.label = label;
    }

    /** Registra que esta conta já saiu num resumo da fila. */
    public void markQueueNoticeSent(Instant now) {
        this.queueNoticeSentAt = now;
    }

    /** Pendente que o dono ainda não viu em nenhum resumo. */
    public boolean isQueueNoticePending() {
        return queueNoticeSentAt == null;
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

    public Instant getQueueNoticeSentAt() {
        return queueNoticeSentAt;
    }

    public Instant getDemoExpiresAt() {
        return demoExpiresAt;
    }
}
