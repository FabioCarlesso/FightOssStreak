package dev.fos.service;

/**
 * O que pode dar errado na entrada por senha (#81), e com que status cada coisa sai.
 *
 * <p>Um tipo só com um motivo dentro, no molde do {@link DemoUnavailableException}: os quatro casos
 * são a mesma família — "esta tentativa de entrar não vai virar sessão" — e o que muda entre eles é
 * o que a tela precisa oferecer em seguida.
 *
 * <p><b>O que estes motivos deliberadamente não distinguem:</b> e-mail inexistente de senha errada.
 * Os dois saem como {@link Motivo#CREDENCIAL_INVALIDA}, porque separá-los transformaria {@code
 * /entrar} em consulta de quem tem conta no app — a mesma razão da D37, agora valendo para o login.
 */
public class PasswordAccessException extends RuntimeException {

    public enum Motivo {
        /** E-mail inexistente ou senha errada. Nunca diga qual dos dois. */
        CREDENCIAL_INVALIDA,
        /** Senha certa, endereço ainda não confirmado. A tela oferece reenviar o link. */
        EMAIL_NAO_VERIFICADO,
        /** Freio de tentativas: por e-mail e por IP. */
        MUITAS_TENTATIVAS,
        /** Ambiente sem credencial de envio — o cadastro por senha não existe aqui. */
        INDISPONIVEL
    }

    private final Motivo motivo;

    private PasswordAccessException(Motivo motivo, String message) {
        super(message);
        this.motivo = motivo;
    }

    public static PasswordAccessException credencialInvalida() {
        return new PasswordAccessException(
                Motivo.CREDENCIAL_INVALIDA, "E-mail ou senha não conferem.");
    }

    public static PasswordAccessException emailNaoVerificado() {
        return new PasswordAccessException(
                Motivo.EMAIL_NAO_VERIFICADO,
                "Confirme seu e-mail para entrar. Se o link venceu, peça outro.");
    }

    public static PasswordAccessException muitasTentativas() {
        return new PasswordAccessException(
                Motivo.MUITAS_TENTATIVAS,
                "Tentativas demais. Espere alguns minutos e tente de" + " novo.");
    }

    public static PasswordAccessException indisponivel() {
        return new PasswordAccessException(
                Motivo.INDISPONIVEL,
                "O cadastro por e-mail e senha não está disponível neste ambiente.");
    }

    public Motivo motivo() {
        return motivo;
    }

    /** Código no corpo do erro — é por ele que a tela escolhe o que mostrar. */
    public String code() {
        return switch (motivo) {
            case CREDENCIAL_INVALIDA -> "credencial_invalida";
            case EMAIL_NAO_VERIFICADO -> "email_nao_verificado";
            case MUITAS_TENTATIVAS -> "muitas_tentativas";
            case INDISPONIVEL -> "cadastro_indisponivel";
        };
    }
}
