package dev.fos.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Próximo grau (#193, D75): a meta que a pessoa digita e as aulas que o diário já registrou.
 *
 * <p>Os mínimos vêm de fora — são o que a academia exige — e por isso são todos opcionais: há
 * academia que não separa com e sem kimono, e há quem só queira contar.
 */
public final class GraduationDtos {

    private GraduationDtos() {}

    /** Teto dos mínimos: barra dígito a mais, e não diz quanto uma academia pode pedir. */
    private static final int MINIMO_MAXIMO = 2000;

    /**
     * A meta como a tela de conta a escreve.
     *
     * @param lastGraduationOn começo da contagem, inclusivo; data futura é recusada
     * @param currentLabel rótulo livre do grau atual ("branca 1º grau") — texto da pessoa, sem
     *     lista fixa de faixas: cada academia nomeia do seu jeito
     */
    public record GoalRequest(
            @NotNull LocalDate lastGraduationOn,
            @Size(max = 60) String currentLabel,
            @Size(max = 60) String nextLabel,
            @Min(0) @Max(MINIMO_MAXIMO) Integer minTotal,
            @Min(0) @Max(MINIMO_MAXIMO) Integer minGi,
            @Min(0) @Max(MINIMO_MAXIMO) Integer minNogi) {}

    public record GoalView(
            LocalDate lastGraduationOn,
            String currentLabel,
            String nextLabel,
            Integer minTotal,
            Integer minGi,
            Integer minNogi) {}

    /**
     * Aulas registradas desde o último grau.
     *
     * @param total todas as sessões {@code AULA}, inclusive as sem marcação de kimono
     * @param unspecified aulas sem marcação — contam no total e em nenhuma das duas metades
     */
    public record ClassCount(int total, int gi, int nogi, int unspecified) {}

    /**
     * A tela inteira do próximo grau. Sem meta configurada, os dois campos vêm nulos e o card da
     * home não aparece.
     */
    public record GraduationView(GoalView goal, ClassCount classes) {}
}
