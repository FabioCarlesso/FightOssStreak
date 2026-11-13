package dev.fos.web.dto;

import dev.fos.model.ProgressStatus;
import dev.fos.model.RequirementStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Requisitos do próximo grau como checklist pessoal (#196, D78).
 *
 * <p>O texto é o que a academia de quem usa entregou, colado pela própria pessoa: dado da conta,
 * nunca currículo (D11).
 */
public final class RequirementDtos {

    private RequirementDtos() {}

    /** Teto do texto colado de uma vez: barra colar um manual inteiro, não uma lista de grau. */
    public static final int MAX_PASTE_LENGTH = 8000;

    /**
     * Colar a lista: um requisito por linha. Linhas vazias e marcadores ({@code •}, {@code -},
     * {@code *}, {@code 1.}, {@code a)}) são limpos no servidor.
     *
     * @param label rótulo do grupo ("branca 2º grau"); em branco mantém o atual
     */
    public record ImportRequest(
            @Size(max = 60) String label, @NotBlank @Size(max = MAX_PASTE_LENGTH) String text) {}

    /**
     * Edição parcial de um requisito: campo ausente fica como está.
     *
     * @param nodeCodes nós ligados, em substituição aos atuais; a lista vazia desliga todos. Só é
     *     gravada quando a pessoa confirma: a sugestão vive na tela, e nunca vira vínculo sozinha
     * @param manualDone marcação à mão; só tem efeito no requisito sem nó
     */
    public record RequirementPatch(
            @Size(min = 1, max = 200) String text,
            Boolean manualDone,
            @Size(max = 10) List<@NotBlank String> nodeCodes) {}

    /** A ordem nova: todos os ids da checklist, cada um uma vez. */
    public record ReorderRequest(@NotNull @Size(max = 200) List<@NotNull Long> ids) {}

    public record LinkedNodeView(String code, String title, ProgressStatus status) {}

    /**
     * @param status derivado do progresso dos nós ligados; só o requisito sem nó usa a marcação à
     *     mão
     * @param manualDone a marcação gravada, para a tela mostrar o estado do controle
     */
    public record RequirementView(
            Long id,
            int order,
            String text,
            RequirementStatus status,
            boolean manualDone,
            List<LinkedNodeView> nodes) {}

    /**
     * A checklist inteira. Sem checklist, {@code label} é nulo e {@code items} vem vazio.
     *
     * @param nodeCodes todos os nós ligados, sem repetir — é o que a home usa para marcar a agenda
     *     e dar preferência no "Continuar", sem reordenar o SRS
     */
    public record ChecklistView(
            String label,
            List<RequirementView> items,
            int done,
            int total,
            List<String> nodeCodes) {}
}
