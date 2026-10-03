package dev.fos.service;

/**
 * O que pode dar errado no login nativo do app (#139, D68), e com que status cada coisa sai.
 *
 * <p>No molde do {@link PasswordAccessException}: uma família — "este login não vai virar token" —
 * com o motivo dentro.
 */
public class MobileLoginException extends RuntimeException {

    public enum Motivo {
        /** O provedor não está configurado neste ambiente: a rota não existe aqui. */
        INDISPONIVEL,
        /** Token do provedor que não confere: assinatura, emissor, audiência, prazo ou nonce. */
        TOKEN_INVALIDO,
        /** Freio por origem. */
        MUITAS_TENTATIVAS
    }

    private final Motivo motivo;

    private MobileLoginException(Motivo motivo, String message) {
        super(message);
        this.motivo = motivo;
    }

    public static MobileLoginException indisponivel() {
        return new MobileLoginException(
                Motivo.INDISPONIVEL, "Este jeito de entrar não está disponível.");
    }

    public static MobileLoginException tokenInvalido() {
        return new MobileLoginException(
                Motivo.TOKEN_INVALIDO, "Não foi possível confirmar o login. Tente de novo.");
    }

    public static MobileLoginException muitasTentativas() {
        return new MobileLoginException(
                Motivo.MUITAS_TENTATIVAS,
                "Tentativas demais. Espere alguns minutos e tente de novo.");
    }

    public Motivo motivo() {
        return motivo;
    }

    public String code() {
        return switch (motivo) {
            case INDISPONIVEL -> "login_indisponivel";
            case TOKEN_INVALIDO -> "token_invalido";
            case MUITAS_TENTATIVAS -> "muitas_tentativas";
        };
    }
}
