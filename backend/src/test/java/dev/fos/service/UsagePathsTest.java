package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O caminho gravado é normalizado contra as rotas do app (#84, D50).
 *
 * <p>O teste que mais importa aqui é o do token: a rota de confirmação de e-mail carrega o token no
 * caminho, e gravá-lo cru colocaria credencial de uso único dentro de uma tabela de métrica.
 */
class UsagePathsTest {

    @Test
    @DisplayName("nenhum segmento variável é gravado — nem token de confirmação, nem de senha")
    void variableSegmentsNeverLand() {
        assertThat(UsagePaths.normalize("/confirmar-email/abc123segredo"))
                .isEqualTo("/confirmar-email/{token}");
        assertThat(UsagePaths.normalize("/senha/redefinir/abc123segredo"))
                .isEqualTo("/senha/redefinir/{token}");
        assertThat(UsagePaths.normalize("/no/M1.2")).isEqualTo("/no/{codigo}");
    }

    @Test
    @DisplayName("query string é descartada — só os três utm_* viajam, e em campo próprio")
    void queryStringIsDropped() {
        assertThat(UsagePaths.normalize("/?utm_source=whatsapp&token=segredo")).isEqualTo("/");
        assertThat(UsagePaths.normalize("/hoje#ancora")).isEqualTo("/hoje");
    }

    @Test
    @DisplayName("rota conhecida passa; o que não é rota conhecida vira /outro")
    void allowlist() {
        assertThat(UsagePaths.normalize("/arvore")).isEqualTo("/arvore");
        assertThat(UsagePaths.normalize("/usuarios")).isEqualTo("/usuarios");
        assertThat(UsagePaths.normalize("/rota-que-nao-existe")).isEqualTo(UsagePaths.OUTRO);
        assertThat(UsagePaths.normalize("/../../etc/passwd")).isEqualTo(UsagePaths.OUTRO);
        assertThat(UsagePaths.normalize(null)).isEqualTo(UsagePaths.OUTRO);
        assertThat(UsagePaths.normalize("")).isEqualTo(UsagePaths.OUTRO);
    }

    @Test
    @DisplayName("barra final e caixa alta não viram linha separada no painel")
    void normalizesShape() {
        assertThat(UsagePaths.normalize("/HOJE/")).isEqualTo("/hoje");
        assertThat(UsagePaths.normalize("hoje")).isEqualTo("/hoje");
        assertThat(UsagePaths.normalize("/")).isEqualTo("/");
    }

    @Test
    @DisplayName("caminho absurdamente longo não é gravado — a coluna tem tamanho, o cliente não")
    void tooLongIsOther() {
        assertThat(UsagePaths.normalize("/no/" + "x".repeat(500))).isEqualTo(UsagePaths.OUTRO);
    }
}
