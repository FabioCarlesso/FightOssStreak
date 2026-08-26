package dev.fos.service;

/**
 * Ação de administração recusada por conflito com o estado atual (#89, #90).
 *
 * <p>Vira <b>409</b> em {@link dev.fos.web.ApiExceptionHandler}, e não 400: o pedido está bem
 * formado e a rota está certa — o que não cabe é a mudança, dado quem a conta é agora. É a mesma
 * leitura que a demonstração já usa para "já há sessão de conta de verdade".
 *
 * <p>Cada motivo tem código próprio no corpo porque a tela precisa dizer <em>qual</em> das guardas
 * bateu: "você não pode se rebaixar" e "esta é a última conta de administração" levam a decisões
 * diferentes de quem está do outro lado.
 */
public class AdminActionException extends RuntimeException {

    public enum Motivo {
        /** Promover a ADMIN uma conta que nunca confirmou o e-mail (D48/D49). */
        EMAIL_NAO_VERIFICADO(
                "admin_email_nao_verificado", "Esta conta ainda não confirmou o e-mail."),
        /** Rebaixar ou bloquear a si mesmo. */
        ACAO_SOBRE_SI("admin_acao_sobre_si", "Você não pode fazer isso com a sua própria conta."),
        /** Deixar o app sem nenhum administrador. */
        ULTIMO_ADMIN("admin_ultimo_admin", "Esta é a última conta de administração do app."),
        /** Conta de demonstração (D39): descartável, não é de ninguém e não aceita ação. */
        CONTA_DE_DEMONSTRACAO(
                "admin_conta_de_demonstracao",
                "Conta de demonstração não é administrada — ela vence sozinha.");

        private final String code;
        private final String message;

        Motivo(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    private final Motivo motivo;

    public AdminActionException(Motivo motivo) {
        super(motivo.message);
        this.motivo = motivo;
    }

    public String code() {
        return motivo.code;
    }
}
