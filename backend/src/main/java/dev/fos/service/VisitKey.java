package dev.fos.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Como contar visitantes sem cookie e sem identificador estável (#84, D50).
 *
 * <p>A chave é o hash de (sal do dia + IP + User-Agent). Ela separa "100 acessos de uma pessoa" de
 * "100 pessoas", que é a única pergunta que a coleta precisa responder sobre indivíduos — e não
 * responde nenhuma outra.
 *
 * <p><b>O sal é sorteado por dia e nunca sai da memória.</b> Duas consequências, as duas
 * deliberadas: o hash não é reversível nem por quem tem o banco inteiro, e a mesma pessoa em dois
 * dias diferentes produz chaves distintas — não há como ligar visita de ontem com visita de hoje.
 * Reiniciar a aplicação sorteia outro sal, então nem a própria aplicação consegue recomputar a
 * chave de um evento passado. É por isso que o app segue sem precisar de banner de consentimento: é
 * resultado do desenho, não sorte.
 *
 * <p>O preço está pago de propósito: sem sessão entre dias, sem funil por pessoa, dado mais grosso.
 * Quem quiser qualquer uma das três coisas precisa reabrir a D50, não contornar esta classe.
 */
@Component
public class VisitKey {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TAMANHO_DO_SAL = 32;

    private final Clock clock;

    private LocalDate diaDoSal;
    private byte[] sal;

    public VisitKey(Clock clock) {
        this.clock = clock;
    }

    /** Hash hexadecimal de 64 caracteres. Entrada nula vira string vazia — nunca estoura. */
    public String of(String ip, String userAgent) {
        byte[] salDeHoje = salDe(LocalDate.now(clock));
        MessageDigest digest = sha256();
        digest.update(salDeHoje);
        digest.update((byte) 0);
        digest.update(nullSafe(ip).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(nullSafe(userAgent).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * Sorteia na virada do dia, e só nela.
     *
     * <p>{@code synchronized} porque duas requisições simultâneas na virada sorteariam dois sais e
     * um dos dois seria descartado — visitas do mesmo dia contadas com chaves de sais diferentes.
     */
    private synchronized byte[] salDe(LocalDate hoje) {
        if (sal == null || !hoje.equals(diaDoSal)) {
            byte[] novo = new byte[TAMANHO_DO_SAL];
            RANDOM.nextBytes(novo);
            sal = novo;
            diaDoSal = hoje;
        }
        return sal;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossivel) {
            // SHA-256 é obrigatório em toda JVM. Se faltar, não há o que tratar.
            throw new IllegalStateException("SHA-256 indisponível", impossivel);
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
