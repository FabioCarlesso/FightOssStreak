package dev.fos.service;

import java.util.Locale;
import java.util.Set;

/**
 * O caminho que a coleta grava — normalizado contra as rotas do app, nunca o que o cliente mandou
 * (#84, D50).
 *
 * <p>Isto é uma guarda de privacidade antes de ser arrumação: a rota do link de confirmação é
 * {@code /confirmar-email/<token>}, e gravar o caminho cru colocaria <b>token de confirmação de
 * e-mail</b> dentro de uma tabela de métrica — exatamente o oposto do que a D50 se comprometeu a
 * fazer. O mesmo vale para a redefinição de senha.
 *
 * <p>Por isso é lista fechada, e não uma lista de coisas a esconder: caminho que não é rota
 * conhecida vira {@link #OUTRO}. Rota nova do app precisa entrar aqui para aparecer no painel, e é
 * um preço barato perto de descobrir depois que algo vazou por uma rota que ninguém lembrou de
 * cobrir.
 */
public final class UsagePaths {

    /** Tudo que não é rota conhecida. Não é erro: é a categoria de "o resto". */
    public static final String OUTRO = "/outro";

    private static final int MAX = 120;

    private static final Set<String> ROTAS =
            Set.of(
                    "/",
                    "/entrar",
                    "/cadastrar",
                    "/confirmar-email",
                    "/senha/esquecida",
                    "/hoje",
                    "/arvore",
                    "/progresso",
                    "/conta",
                    "/feedback",
                    "/usuarios",
                    "/admin/painel");

    private UsagePaths() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return OUTRO;
        }
        String path = raw.trim();
        // Fora a query string e o fragmento. Os `utm_*` chegam em campo próprio; o resto da query
        // é descartado, e está escrito em docs/11-privacidade.md que é assim.
        int corte = indexOfAny(path, '?', '#');
        if (corte >= 0) {
            path = path.substring(0, corte);
        }
        if (path.isEmpty()) {
            return OUTRO;
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        path = path.toLowerCase(Locale.ROOT);
        // Barra final some, menos na raiz — senão `/hoje` e `/hoje/` viram duas linhas do painel.
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.length() > MAX) {
            return OUTRO;
        }
        if (ROTAS.contains(path)) {
            return path;
        }
        // As três rotas com segmento variável. O segmento não é gravado — nem o código do nó, que
        // seria inofensivo, para que a regra continue sendo "segmento variável não entra" e não
        // "estes segmentos variáveis não entram".
        if (path.startsWith("/no/")) {
            return "/no/{codigo}";
        }
        if (path.startsWith("/confirmar-email/")) {
            return "/confirmar-email/{token}";
        }
        if (path.startsWith("/senha/redefinir/")) {
            return "/senha/redefinir/{token}";
        }
        return OUTRO;
    }

    private static int indexOfAny(String value, char a, char b) {
        int primeiro = value.indexOf(a);
        int segundo = value.indexOf(b);
        if (primeiro < 0) {
            return segundo;
        }
        return segundo < 0 ? primeiro : Math.min(primeiro, segundo);
    }
}
