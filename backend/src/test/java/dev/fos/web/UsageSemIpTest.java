package dev.fos.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * A promessa que o schema tem de cumprir: <b>não existe coluna de endereço de IP</b> (#84, D50).
 *
 * <p>Este teste é o critério de aceite da issue virado código, e ele é sobre o schema INTEIRO, não
 * sobre as tabelas da coleta: {@code docs/11-privacidade.md} promete, com todas as letras, que o
 * app não guarda IP em lugar nenhum. Uma coluna nova em qualquer migration futura reprova aqui, que
 * é exatamente o momento em que a decisão precisa ser consciente — se um dia guardar IP for
 * necessário, o caminho é reabrir a D50 e reescrever a promessa, não passar por baixo dela.
 *
 * <p>Varre as duas fontes de propósito: o banco migrado (o que existe de verdade) e o texto das
 * migrations (que pega uma coluna criada e removida depois, cujo dado chegou a existir).
 */
@SpringBootTest
@ActiveProfiles("test")
class UsageSemIpTest {

    /** Segmentos de nome que denunciam armazenamento de endereço de rede. */
    private static final Set<String> PROIBIDOS =
            Set.of("ip", "ips", "ipaddress", "ipaddr", "inet", "remoteaddr", "clientip", "xff");

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("nenhuma coluna do banco guarda endereço de IP")
    void noIpColumnInTheDatabase() throws SQLException {
        List<String> suspeitas = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet colunas = metadata.getColumns(null, null, "%", "%")) {
                while (colunas.next()) {
                    String tabela = colunas.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                    String coluna = colunas.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                    // Só o schema da aplicação: o H2 expõe catálogos de sistema inteiros aqui.
                    if (tabela.startsWith("flyway_")
                            || ehDoSistema(colunas.getString("TABLE_SCHEM"))) {
                        continue;
                    }
                    if (pareceIp(coluna)) {
                        suspeitas.add(tabela + "." + coluna);
                    }
                }
            }
        }

        assertThat(suspeitas)
                .as(
                        "docs/11-privacidade.md promete que o app não guarda endereço de IP."
                                + " Se guardar virou necessidade, reabra a D50 e reescreva a promessa"
                                + " — não afrouxe este teste.")
                .isEmpty();
    }

    @Test
    @DisplayName("nenhuma migration jamais criou coluna de IP — nem uma que tenha sido removida")
    void noIpColumnInAnyMigration() throws IOException {
        Path migrations = Path.of("src/main/resources/db/migration");
        List<String> suspeitas = new ArrayList<>();
        try (Stream<Path> arquivos = Files.list(migrations)) {
            for (Path arquivo : arquivos.filter(p -> p.toString().endsWith(".sql")).toList()) {
                for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                    String limpa = linha.trim().toLowerCase(Locale.ROOT);
                    // Comentário explica por que NÃO há coluna de IP — e fala de "ip" o tempo todo.
                    if (limpa.startsWith("--") || limpa.isEmpty()) {
                        continue;
                    }
                    String primeiraPalavra = limpa.split("[\\s(,]+", 2)[0];
                    if (pareceIp(primeiraPalavra)) {
                        suspeitas.add(arquivo.getFileName() + ": " + limpa);
                    }
                }
            }
        }

        assertThat(suspeitas).isEmpty();
    }

    private static boolean ehDoSistema(String schema) {
        if (schema == null) {
            return false;
        }
        String nome = schema.toUpperCase(Locale.ROOT);
        return nome.startsWith("INFORMATION_SCHEMA")
                || nome.startsWith("PG_")
                || nome.equals("SYS");
    }

    /** Casa por segmento, e não por substring: "recipient" e "description" não são endereço. */
    private static boolean pareceIp(String nome) {
        for (String segmento : nome.split("_")) {
            if (PROIBIDOS.contains(segmento)) {
                return true;
            }
        }
        return false;
    }
}
