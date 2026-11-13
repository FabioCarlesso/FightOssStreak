package dev.fos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.fos.model.ProgressStatus;
import dev.fos.model.RequirementStatus;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * As duas regras puras da checklist do próximo grau (#196, D78), com os mesmos casos de {@code
 * shared/domain/src/requisitos.test.ts} (D17): se um lado mudar sem o outro, um destes reprova.
 */
class GraduationRequirementServiceTest {

    @Test
    @DisplayName("parse: tira marcadores e linhas vazias, sem confundir número com marcador")
    void parse() {
        assertThat(
                        GraduationRequirementService.parse(
                                "• Fuga de montada\r\n\r\n  - 2. Armlock\n1) Guarda\n"
                                        + "100kg: manutenção\n(3)  Tempo   de faixa\na) Etiqueta\n"
                                        + "1.5 metros de distância\n* * Duplo marcador"))
                .containsExactly(
                        "Fuga de montada",
                        "Armlock",
                        "Guarda",
                        "100kg: manutenção",
                        "Tempo de faixa",
                        "Etiqueta",
                        "1.5 metros de distância",
                        "Duplo marcador");
        assertThat(GraduationRequirementService.parse("\n - \n•\n")).isEmpty();
    }

    @Test
    @DisplayName("parse: NBSP e outros espaços Unicode contam como espaço, como no JS")
    void parseTreatsUnicodeSpacesAsSpaces() {
        assertThat(
                        GraduationRequirementService.parse(
                                "\u00A0•\u00A0Armlock\u00A0da montada\n"
                                        + "b)\u00A0Passagem de guarda\n"
                                        + "\u2003-\u202FFuga\u3000de\u2009montada\uFEFF\n"
                                        + "\u00A0\u00A0"))
                .containsExactly("Armlock da montada", "Passagem de guarda", "Fuga de montada");
    }

    @Test
    @DisplayName("parse: linha acima do teto é recusada apontando a linha, e não cortada")
    void parseRejectsLongLines() {
        assertThatThrownBy(() -> GraduationRequirementService.parse("ok\n" + "x".repeat(201)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("linha 2");
    }

    @Test
    @DisplayName("status: derivado do progresso gravado; sem nó, da marcação à mão")
    void status() {
        assertThat(GraduationRequirementService.statusOf(List.of(), false))
                .isEqualTo(RequirementStatus.PENDENTE);
        assertThat(GraduationRequirementService.statusOf(List.of(), true))
                .isEqualTo(RequirementStatus.CONCLUIDO);
        assertThat(GraduationRequirementService.statusOf(Arrays.asList(null, null), true))
                .as("marcação à mão não vale para requisito com nó")
                .isEqualTo(RequirementStatus.PENDENTE);
        assertThat(
                        GraduationRequirementService.statusOf(
                                Arrays.asList(ProgressStatus.IN_PROGRESS, null), false))
                .isEqualTo(RequirementStatus.EM_ANDAMENTO);
        assertThat(
                        GraduationRequirementService.statusOf(
                                Arrays.asList(ProgressStatus.COMPLETED, null), false))
                .isEqualTo(RequirementStatus.EM_ANDAMENTO);
        assertThat(
                        GraduationRequirementService.statusOf(
                                List.of(ProgressStatus.COMPLETED, ProgressStatus.COMPLETED), false))
                .isEqualTo(RequirementStatus.CONCLUIDO);
    }
}
