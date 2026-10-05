package dev.fos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** O que vale como origem dos links de e-mail (FOS-02), e o que vale como ausente. */
class FosPropertiesPublicUrlTest {

    @ParameterizedTest
    @CsvSource({
        "https://fos.up.railway.app, https://fos.up.railway.app",
        "https://fos.up.railway.app/, https://fos.up.railway.app",
        "'  https://fos.example.test:8443  ', https://fos.example.test:8443",
        "http://localhost:5173, http://localhost:5173",
        "http://127.0.0.1:8081/, http://127.0.0.1:8081"
    })
    void acceptsAnAbsoluteOrigin(String configured, String expected) {
        FosProperties properties = comUrl(configured);

        assertThat(properties.hasPublicUrl()).isTrue();
        assertThat(properties.publicUrl()).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "   ",
                // Sem TLS fora de localhost: o token viajaria em claro.
                "http://fos.up.railway.app",
                "fos.up.railway.app",
                "/relativo",
                "ftp://fos.example.test",
                "javascript:alert(1)",
                // Não é origem: caminho, query e credencial embutida iriam parar no link.
                "https://fos.example.test/app",
                "https://fos.example.test?x=1",
                "https://fos.example.test#x",
                "https://quem@evil.test",
                "https://exemplo com espaço.test"
            })
    void treatsAnythingElseAsAbsent(String configured) {
        FosProperties properties = comUrl(configured);

        assertThat(properties.hasPublicUrl()).isFalse();
        assertThat(properties.publicUrl()).isEmpty();
    }

    private static FosProperties comUrl(String url) {
        return new FosProperties(null, null, null, null, null, null, null, null, null, url, null);
    }
}
