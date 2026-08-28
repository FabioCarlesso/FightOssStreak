package dev.fos.service;

import dev.fos.model.UsageEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * País e região a partir do IP, com base local (#84, D50).
 *
 * <p>Local, e não um serviço de consulta: chamar terceiro a cada requisição colocaria o IP de quem
 * usa o app na mão de outra empresa — que é precisamente o que {@code docs/11-privacidade.md}
 * promete que não acontece. A base é um arquivo, entra por PR e é atualizada por PR.
 *
 * <p><b>Ausente é o caso normal.</b> Dev e CI sobem sem base nenhuma, e coletam tudo menos país: o
 * país vira {@link UsageEvent#PAIS_DESCONHECIDO}, que é categoria própria e não erro. Base ausente
 * nunca derruba a subida — a coleta é a parte menos importante de tudo que roda aqui.
 *
 * <p>Formato: CSV de {@code início,fim,país[,região]}, com ou sem aspas, uma linha por faixa,
 * ordenada ou não — é a forma do DB-IP Lite (CC BY 4.0), que é o que a issue indicou. Aceita {@code
 * .gz} pelo nome do arquivo, porque a base descompactada não é coisa de versionar.
 */
public class GeoIpDatabase {

    private static final Logger log = LoggerFactory.getLogger(GeoIpDatabase.class);

    /** Faixa fechada nos dois lados, com os extremos já em bytes de rede. */
    private record Faixa(byte[] inicio, byte[] fim, String pais, String regiao) {}

    private final List<Faixa> v4;
    private final List<Faixa> v6;

    private GeoIpDatabase(List<Faixa> v4, List<Faixa> v6) {
        this.v4 = v4;
        this.v6 = v6;
    }

    /** A base que não sabe de nada. É o que dev e CI usam. */
    public static GeoIpDatabase empty() {
        return new GeoIpDatabase(List.of(), List.of());
    }

    public boolean isEmpty() {
        return v4.isEmpty() && v6.isEmpty();
    }

    /**
     * Carrega, ou devolve a base vazia.
     *
     * <p>Engole qualquer falha de leitura de propósito: arquivo corrompido, permissão negada ou
     * caminho errado não podem impedir a aplicação de subir. O aviso vai para o log e a coleta
     * segue sem país.
     */
    public static GeoIpDatabase load(String caminho) {
        if (caminho == null || caminho.isBlank()) {
            log.info("Sem base de geolocalização: a coleta de uso segue, com país desconhecido");
            return empty();
        }
        Path arquivo = Path.of(caminho.trim());
        if (!Files.isReadable(arquivo)) {
            log.warn(
                    "Base de geolocalização não encontrada em {}: a coleta segue, com país"
                            + " desconhecido",
                    arquivo);
            return empty();
        }
        try (InputStream bruto = Files.newInputStream(arquivo);
                InputStream stream =
                        arquivo.getFileName().toString().endsWith(".gz")
                                ? new GZIPInputStream(bruto)
                                : bruto) {
            GeoIpDatabase base = parse(stream);
            log.info(
                    "Base de geolocalização carregada de {}: {} faixas IPv4 e {} IPv6",
                    arquivo,
                    base.v4.size(),
                    base.v6.size());
            return base;
        } catch (IOException | RuntimeException falha) {
            log.warn(
                    "Base de geolocalização em {} não pôde ser lida ({}): a coleta segue, com país"
                            + " desconhecido",
                    arquivo,
                    falha.toString());
            return empty();
        }
    }

    static GeoIpDatabase parse(InputStream stream) throws IOException {
        List<Faixa> v4 = new ArrayList<>();
        List<Faixa> v6 = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                String limpa = linha.trim();
                if (limpa.isEmpty() || limpa.startsWith("#")) {
                    continue;
                }
                String[] colunas = limpa.split(",");
                if (colunas.length < 3) {
                    continue;
                }
                byte[] inicio = endereco(unquote(colunas[0]));
                byte[] fim = endereco(unquote(colunas[1]));
                if (inicio == null || fim == null || inicio.length != fim.length) {
                    continue;
                }
                String pais = unquote(colunas[2]).toUpperCase(Locale.ROOT);
                if (pais.length() != 2) {
                    continue;
                }
                String regiao = colunas.length > 3 ? unquote(colunas[3]) : "";
                Faixa faixa =
                        new Faixa(
                                inicio,
                                fim,
                                pais,
                                regiao.isBlank() ? UsageEvent.DESCONHECIDO : regiao);
                (inicio.length == 4 ? v4 : v6).add(faixa);
            }
        }
        // Ordenar aqui, e não confiar no arquivo: a busca binária depende disso, e uma base fora
        // de ordem devolveria país errado em silêncio — pior que devolver desconhecido.
        v4.sort((a, b) -> compare(a.inicio(), b.inicio()));
        v6.sort((a, b) -> compare(a.inicio(), b.inicio()));
        return new GeoIpDatabase(
                Collections.unmodifiableList(v4), Collections.unmodifiableList(v6));
    }

    /** País ISO de duas letras, ou {@code ZZ}. Nunca estoura: IP ilegível é desconhecido. */
    public String country(String ip) {
        Faixa faixa = find(ip);
        return faixa == null ? UsageEvent.PAIS_DESCONHECIDO : faixa.pais();
    }

    public String region(String ip) {
        Faixa faixa = find(ip);
        return faixa == null ? UsageEvent.DESCONHECIDO : faixa.regiao();
    }

    private Faixa find(String ip) {
        byte[] endereco = endereco(ip);
        if (endereco == null) {
            return null;
        }
        List<Faixa> faixas = endereco.length == 4 ? v4 : v6;
        // Maior início <= endereço. `end` fica no primeiro início ESTRITAMENTE maior.
        int baixo = 0;
        int alto = faixas.size();
        while (baixo < alto) {
            int meio = (baixo + alto) >>> 1;
            if (compare(faixas.get(meio).inicio(), endereco) <= 0) {
                baixo = meio + 1;
            } else {
                alto = meio;
            }
        }
        if (baixo == 0) {
            return null;
        }
        Faixa candidata = faixas.get(baixo - 1);
        // A faixa anterior pode simplesmente terminar antes — buraco na base é comum.
        return compare(endereco, candidata.fim()) <= 0 ? candidata : null;
    }

    private static byte[] endereco(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        try {
            // `getByName` só resolve nome quando o texto NÃO é um IP literal; aqui a entrada vem
            // do socket ou de uma coluna de base, então não há consulta de DNS acontecendo.
            return InetAddress.getByName(ip.trim()).getAddress();
        } catch (UnknownHostException naoEUmIp) {
            return null;
        }
    }

    /** Comparação sem sinal, byte a byte: em Java {@code byte} é assinado e 0xC0 seria negativo. */
    private static int compare(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return Integer.compare(a.length, b.length);
        }
        for (int i = 0; i < a.length; i++) {
            int diferenca = (a[i] & 0xFF) - (b[i] & 0xFF);
            if (diferenca != 0) {
                return diferenca;
            }
        }
        return 0;
    }

    private static String unquote(String value) {
        String limpo = value.trim();
        if (limpo.length() >= 2 && limpo.startsWith("\"") && limpo.endsWith("\"")) {
            return limpo.substring(1, limpo.length() - 1).trim();
        }
        return limpo;
    }
}
