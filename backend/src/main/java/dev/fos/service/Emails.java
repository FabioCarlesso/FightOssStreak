package dev.fos.service;

import java.util.Locale;

/**
 * A forma canônica de um endereço.
 *
 * <p>Existe como classe própria porque o endereço é <b>chave</b> em dois lugares — {@code
 * user_identity.provider_subject} do provedor {@code password} e {@code app_user.primary_email} — e
 * chave que entra em duas formas diferentes deixa de ser chave: {@code Ana@Example.test} criaria
 * uma segunda conta para quem já tem uma.
 *
 * <p>Morava no {@code EmailAccessService} da #52, que saiu com a fila de aprovação (D48). Ficar
 * pendurada num serviço que não existe mais era o que faltava para alguém reimplementar a
 * normalização em outro canto, com outra regra.
 */
public final class Emails {

    private Emails() {}

    /** Sem espaço nas pontas e em minúsculas. Nulo vira vazio, para nunca explodir na borda. */
    public static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
