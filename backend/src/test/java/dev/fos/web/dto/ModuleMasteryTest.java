package dev.fos.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.fos.model.ProgressStatus;
import dev.fos.web.dto.CurriculumDtos.ModuleView;
import dev.fos.web.dto.CurriculumDtos.NodeSummaryView;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModuleMasteryTest {

    @Test
    @DisplayName("módulo sem nó concluído é 0%")
    void semProgresso() {
        ModuleView module = module(ProgressStatus.AVAILABLE, ProgressStatus.LOCKED);

        assertThat(module.completedNodes()).isZero();
        assertThat(module.totalNodes()).isEqualTo(2);
        assertThat(module.masteryPercent()).isZero();
    }

    @Test
    @DisplayName("em andamento não conta como dominado")
    void emAndamentoNaoConta() {
        assertThat(module(ProgressStatus.IN_PROGRESS, ProgressStatus.COMPLETED).masteryPercent())
                .isEqualTo(50);
    }

    @Test
    @DisplayName("arredonda para baixo: 100% só com todos concluídos")
    void arredondaParaBaixo() {
        assertThat(
                        module(
                                        ProgressStatus.COMPLETED,
                                        ProgressStatus.COMPLETED,
                                        ProgressStatus.AVAILABLE)
                                .masteryPercent())
                .isEqualTo(66);
        assertThat(ModuleView.masteryPercent(199, 200)).isEqualTo(99);
        assertThat(module(ProgressStatus.COMPLETED, ProgressStatus.COMPLETED).masteryPercent())
                .isEqualTo(100);
    }

    @Test
    @DisplayName("módulo vazio é 0%, sem divisão por zero")
    void moduloVazio() {
        assertThat(ModuleView.of("M9", "Vazio", null, List.of()).masteryPercent()).isZero();
    }

    private static ModuleView module(ProgressStatus... statuses) {
        List<NodeSummaryView> nodes =
                Arrays.stream(statuses)
                        .map(
                                status ->
                                        new NodeSummaryView(
                                                "X", "X", null, status, null, List.of(), false, 0,
                                                null, null))
                        .toList();
        return ModuleView.of("M0", "Módulo", null, nodes);
    }
}
