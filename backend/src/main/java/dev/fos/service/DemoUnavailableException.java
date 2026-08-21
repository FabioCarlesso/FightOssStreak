package dev.fos.service;

/**
 * A demonstração não pode ser aberta agora — e o motivo muda o que a tela faz.
 *
 * <p>Três motivos, três respostas HTTP, e a diferença importa: <b>não configurada</b> é "este
 * ambiente não tem conta-modelo", permanente até alguém configurar; <b>lotada</b> é "tente de novo
 * daqui a pouco", que é o teto de simultâneas e o freio por IP; <b>sessão existente</b> é "você já
 * está logado", e trocar essa sessão por uma descartável em silêncio seria pior que recusar. Nenhum
 * dos três cria conta.
 *
 * <p>O motivo é um enum, e não um par de booleanos, porque quem traduz para status HTTP é o {@code
 * ApiExceptionHandler} — a camada de serviço não conhece código de resposta.
 */
public class DemoUnavailableException extends RuntimeException {

    public enum Motivo {
        /** Sem {@code fos.demo.template-email} (ou apontando para conta inexistente). */
        NAO_CONFIGURADA("demo_indisponivel"),
        /** Teto de demonstrações vivas, ou freio por IP. */
        LOTADA("demo_lotado"),
        /** Já existe sessão de uma conta de verdade neste navegador. */
        SESSAO_EXISTENTE("sessao_existente");

        private final String code;

        Motivo(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Motivo motivo;

    private DemoUnavailableException(Motivo motivo, String message) {
        super(message);
        this.motivo = motivo;
    }

    public static DemoUnavailableException notConfigured() {
        return new DemoUnavailableException(
                Motivo.NAO_CONFIGURADA, "Este ambiente não tem demonstração configurada.");
    }

    public static DemoUnavailableException busy(String message) {
        return new DemoUnavailableException(Motivo.LOTADA, message);
    }

    /**
     * Sessão de gente de verdade não vira demonstração sem aviso.
     *
     * <p>O caso real: o autor, logado, abre a apresentação pelo rodapé e clica no botão. Como a
     * demonstração é cópia da conta-modelo — que pode ser a conta dele —, ela se parece com o app
     * dele, e o que for registrado ali morre no prazo.
     */
    public static DemoUnavailableException alreadySignedIn() {
        return new DemoUnavailableException(
                Motivo.SESSAO_EXISTENTE,
                "Você já está com uma sessão aberta neste navegador. Saia dela antes de abrir uma"
                        + " demonstração.");
    }

    public Motivo motivo() {
        return motivo;
    }

    public String code() {
        return motivo.code();
    }
}
