package dev.fos.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * O que conta como senha aceitável (#81).
 *
 * <p><b>Comprimento, e não composição.</b> A linha é a do NIST SP 800-63B: exigir maiúscula, número
 * e símbolo não mede força — produz {@code Senha@2026} — enquanto o comprimento mede. Doze
 * caracteres, sem regra de forma, e nenhuma troca periódica obrigatória.
 *
 * <p><b>Teto de 72 bytes, e ele não é arbitrário.</b> O bcrypt ignora o que passar disso, e o
 * Spring Security recusa a entrada em vez de truncar em silêncio — sem esta guarda, uma senha longa
 * viraria 500 no cadastro. Como o limite é de <em>bytes</em>, acentos e emoji contam mais de um.
 *
 * <p><b>A lista de óbvias é curta e embutida de propósito.</b> Um serviço externo de senhas vazadas
 * seria uma dependência de rede no caminho do cadastro e um segredo a mais para configurar — e a
 * aplicação sobe sem segredo nenhum (regra 4 do CLAUDE.md). O que esta lista pega são as senhas que
 * alguém digita para "passar da tela": sequências e a própria palavra senha esticadas até doze
 * caracteres. Não pretende ser mais que isso.
 */
public final class PasswordPolicy {

    public static final int MINIMO = 12;

    /** Teto do bcrypt, em bytes. Ver o javadoc da classe. */
    public static final int MAXIMO_BYTES = 72;

    /**
     * Senhas que já têm doze caracteres e mesmo assim não valem nada.
     *
     * <p>Só faz sentido listar as que passariam pelo mínimo — {@code 123456} é recusado pelo
     * comprimento antes de chegar aqui.
     */
    private static final Set<String> OBVIAS =
            Set.of(
                    "123456789012",
                    "1234567890123",
                    "12345678901234",
                    "123456789012345",
                    "senha12345678",
                    "senha123456789",
                    "password12345",
                    "password123456",
                    "qwertyuiop123",
                    "qwertyuiopasdf",
                    "aaaaaaaaaaaa",
                    "abcdefghijkl",
                    "abcdefghijklm",
                    "fightossstreak",
                    "jiujitsu12345");

    private PasswordPolicy() {}

    /**
     * Recusa a senha explicando o motivo, ou não faz nada.
     *
     * <p>{@link IllegalArgumentException} porque o handler da API já a traduz em 400 {@code
     * invalid_request} com a mensagem no corpo — é ela que a tela mostra.
     */
    public static void check(String password, String email) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Informe uma senha.");
        }
        if (password.length() < MINIMO) {
            throw new IllegalArgumentException(
                    "A senha precisa de pelo menos " + MINIMO + " caracteres.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
            throw new IllegalArgumentException(
                    "A senha é longa demais (máximo de "
                            + MAXIMO_BYTES
                            + " bytes; acentos e emoji contam mais de um).");
        }
        String normalizada = password.toLowerCase(Locale.ROOT);
        if (OBVIAS.contains(normalizada)) {
            throw new IllegalArgumentException("Esta senha é fácil demais de adivinhar.");
        }
        // O próprio endereço é a primeira coisa que alguém tenta, e quem tenta já o conhece.
        if (email != null
                && !email.isBlank()
                && normalizada.contains(email.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("A senha não pode conter o seu e-mail.");
        }
    }
}
