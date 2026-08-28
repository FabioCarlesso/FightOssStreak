package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.UsageEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * País e região a partir do IP, com base local (#84, D50).
 *
 * <p>O caso que mais interessa é o de baixo: <b>sem base</b>. É como dev e CI rodam, e a coleta
 * inteira tem de funcionar assim — com país desconhecido virando categoria própria, não erro.
 */
class GeoIpDatabaseTest {

    private static GeoIpDatabase base(String csv) throws IOException {
        return GeoIpDatabase.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("sem base, tudo é país desconhecido — e nada estoura")
    void emptyDatabase() {
        GeoIpDatabase vazia = GeoIpDatabase.empty();

        assertThat(vazia.isEmpty()).isTrue();
        assertThat(vazia.country("203.0.113.7")).isEqualTo(UsageEvent.PAIS_DESCONHECIDO);
        assertThat(vazia.region("203.0.113.7")).isEqualTo(UsageEvent.DESCONHECIDO);
    }

    @Test
    @DisplayName("caminho que não existe devolve a base vazia em vez de derrubar a subida")
    void missingFileIsNotAFailure() {
        assertThat(GeoIpDatabase.load("/caminho/que/nao/existe.csv").isEmpty()).isTrue();
        assertThat(GeoIpDatabase.load("").isEmpty()).isTrue();
        assertThat(GeoIpDatabase.load(null).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("arquivo .gz vazio ou corrompido é base vazia, não subida derrubada")
    void unreadableFileIsNotAFailure(@TempDir Path pasta) throws IOException {
        // Este é exatamente o que o `backend/Dockerfile` deixa quando o download da base falha ou
        // é pulado (`--build-arg GEOIP=false`, como o CI faz): o arquivo existe, com zero byte, e
        // `FOS_USAGE_GEOIP_DATABASE` aponta para ele. Se isso derrubasse a subida,
        // indisponibilidade
        // do db-ip.com viraria deploy falho — que é o oposto do que a D50 combinou.
        Path vazio = pasta.resolve("geoip.csv.gz");
        Files.write(vazio, new byte[0]);
        assertThat(GeoIpDatabase.load(vazio.toString()).isEmpty()).isTrue();

        Path lixo = pasta.resolve("corrompida.csv.gz");
        Files.writeString(lixo, "isto não é gzip nenhum");
        assertThat(GeoIpDatabase.load(lixo.toString()).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("faixa encontrada devolve país e região; buraco entre faixas devolve desconhecido")
    void lookup() throws IOException {
        GeoIpDatabase base =
                base(
                        """
                        "1.0.0.0","1.0.0.255","AU","Queensland"
                        "203.0.113.0","203.0.113.255","BR","Sao Paulo"
                        """);

        assertThat(base.country("203.0.113.7")).isEqualTo("BR");
        assertThat(base.region("203.0.113.7")).isEqualTo("Sao Paulo");
        assertThat(base.country("1.0.0.5")).isEqualTo("AU");
        // Entre as duas faixas: buraco na base é comum, e não é erro.
        assertThat(base.country("8.8.8.8")).isEqualTo(UsageEvent.PAIS_DESCONHECIDO);
        // Antes da primeira faixa: o outro extremo da busca binária.
        assertThat(base.country("0.0.0.1")).isEqualTo(UsageEvent.PAIS_DESCONHECIDO);
    }

    @Test
    @DisplayName("arquivo fora de ordem é ordenado no carregamento — a busca binária depende disso")
    void unsortedFileIsSorted() throws IOException {
        GeoIpDatabase base =
                base(
                        """
                        203.0.113.0,203.0.113.255,BR
                        1.0.0.0,1.0.0.255,AU
                        """);

        assertThat(base.country("1.0.0.5")).isEqualTo("AU");
        assertThat(base.country("203.0.113.7")).isEqualTo("BR");
    }

    @Test
    @DisplayName("byte alto não vira negativo: 192.x seria menor que 1.x com comparação assinada")
    void unsignedComparison() throws IOException {
        GeoIpDatabase base = base("192.168.0.0,192.168.255.255,BR\n");

        assertThat(base.country("192.168.1.1")).isEqualTo("BR");
        assertThat(base.country("193.0.0.1")).isEqualTo(UsageEvent.PAIS_DESCONHECIDO);
    }

    @Test
    @DisplayName("IPv6 tem lista própria — misturar com IPv4 daria país errado, não desconhecido")
    void ipv6() throws IOException {
        GeoIpDatabase base =
                base(
                        """
                        1.0.0.0,1.0.0.255,AU
                        2001:db8::,2001:db8::ffff,BR,Bahia
                        """);

        assertThat(base.country("2001:db8::1")).isEqualTo("BR");
        assertThat(base.country("2001:db9::1")).isEqualTo(UsageEvent.PAIS_DESCONHECIDO);
    }

    @Test
    @DisplayName("linha torta é pulada, não derruba o carregamento inteiro")
    void malformedLinesAreSkipped() throws IOException {
        GeoIpDatabase base =
                base(
                        """
                        # comentário
                        linha,torta
                        nao-e-ip,tambem-nao,BR
                        1.0.0.0,1.0.0.255,PAIS-GRANDE-DEMAIS
                        203.0.113.0,203.0.113.255,BR
                        """);

        assertThat(base.country("203.0.113.7")).isEqualTo("BR");
        assertThat(base.country("1.0.0.5")).isEqualTo(UsageEvent.PAIS_DESCONHECIDO);
    }

    @Test
    @DisplayName("sem coluna de região, região é desconhecida — a base do DB-IP de país não tem")
    void regionIsOptional() throws IOException {
        GeoIpDatabase base = base("203.0.113.0,203.0.113.255,BR\n");

        assertThat(base.country("203.0.113.7")).isEqualTo("BR");
        assertThat(base.region("203.0.113.7")).isEqualTo(UsageEvent.DESCONHECIDO);
    }
}
