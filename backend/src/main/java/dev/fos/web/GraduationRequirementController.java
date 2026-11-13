package dev.fos.web;

import dev.fos.service.CurrentUserProvider;
import dev.fos.service.GraduationRequirementService;
import dev.fos.web.dto.RequirementDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Requisitos do próximo grau como checklist pessoal (#196, D78).
 *
 * <p>Tudo aqui é da conta que chama, e de mais ninguém: o id de requisito de outra conta responde
 * 404, como o de requisito que não existe.
 */
@RestController
@RequestMapping("/api/graduacao/requisitos")
@Tag(name = "Graduação", description = "Meta do próximo grau e aulas registradas desde o último")
public class GraduationRequirementController {

    private final GraduationRequirementService requisitos;
    private final CurrentUserProvider currentUser;

    public GraduationRequirementController(
            GraduationRequirementService requisitos, CurrentUserProvider currentUser) {
        this.requisitos = requisitos;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(
            summary = "Checklist do próximo grau, com o status de cada requisito",
            description = "O status vem do progresso dos nós ligados; sem nó, da marcação à mão.")
    public RequirementDtos.ChecklistView checklist() {
        return requisitos.get(currentUser.currentUserId());
    }

    @PostMapping("/importar")
    @Operation(
            summary = "Cola a lista da academia: um requisito por linha, acrescentado ao fim",
            description =
                    "Linhas vazias e marcadores (•, -, *, 1., a)) são limpos. Acima do teto de"
                            + " itens ou de tamanho de linha responde 400.")
    public RequirementDtos.ChecklistView importText(
            @Valid @RequestBody RequirementDtos.ImportRequest request) {
        return requisitos.importText(currentUser.currentUserId(), request);
    }

    @PatchMapping("/{id}")
    @Operation(
            summary = "Edita um requisito: texto, marcação à mão ou nós ligados",
            description = "Campo ausente fica como está. `nodeCodes` substitui os nós ligados.")
    public RequirementDtos.ChecklistView update(
            @PathVariable Long id, @Valid @RequestBody RequirementDtos.RequirementPatch patch) {
        return requisitos.update(currentUser.currentUserId(), id, patch);
    }

    @PutMapping("/ordem")
    @Operation(summary = "Reordena a checklist: todos os ids, cada um uma vez")
    public RequirementDtos.ChecklistView reorder(
            @Valid @RequestBody RequirementDtos.ReorderRequest request) {
        return requisitos.reorder(currentUser.currentUserId(), request.ids());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove um requisito")
    public RequirementDtos.ChecklistView delete(@PathVariable Long id) {
        return requisitos.delete(currentUser.currentUserId(), id);
    }

    @DeleteMapping
    @Operation(summary = "Apaga a checklist inteira; nós e progresso não são tocados")
    public ResponseEntity<Void> deleteAll() {
        requisitos.deleteAll(currentUser.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
