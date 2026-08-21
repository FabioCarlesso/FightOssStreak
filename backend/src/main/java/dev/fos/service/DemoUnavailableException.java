package dev.fos.service;

/**
 * A demonstração não existe neste ambiente, ou não cabe mais uma agora.
 *
 * <p>Dois motivos, dois códigos, e a diferença importa para a tela: {@code demo_indisponivel} é
 * "este ambiente não tem conta-modelo configurada" — permanente até alguém configurar; {@code
 * demo_lotado} é "tente de novo daqui a pouco", que é o teto de demonstrações vivas e o freio por
 * IP. Nenhum dos dois cria conta.
 */
public class DemoUnavailableException extends RuntimeException {

    private final String code;
    private final boolean temporary;

    private DemoUnavailableException(String code, boolean temporary, String message) {
        super(message);
        this.code = code;
        this.temporary = temporary;
    }

    /** Sem {@code fos.demo.template-email} configurado (ou apontando para conta inexistente). */
    public static DemoUnavailableException notConfigured() {
        return new DemoUnavailableException(
                "demo_indisponivel", false, "Este ambiente não tem demonstração configurada.");
    }

    /** Teto de demonstrações vivas, ou freio por IP. */
    public static DemoUnavailableException busy(String message) {
        return new DemoUnavailableException("demo_lotado", true, message);
    }

    public String code() {
        return code;
    }

    /** Verdadeiro quando faz sentido tentar de novo mais tarde — é o que separa 429 de 404. */
    public boolean isTemporary() {
        return temporary;
    }
}
