package dev.fos.web;

import dev.fos.service.UsageCollector;
import dev.fos.web.dto.UsageDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A porta por onde o web conta que mudou de rota (#84, D50).
 *
 * <p>Pública: o primeiro acesso que interessa contar é o de quem ainda não tem conta — a landing
 * (D33) é a página que recebe o link. Exigir sessão aqui mediria só quem já entrou, que é
 * exatamente o público sobre o qual não faltava informação.
 *
 * <p>Responde 204 sempre, inclusive quando o evento não é gravado. Não é preguiça: o cliente não
 * tem nada a fazer com a falha, e um erro visível aqui convidaria a tela a tratá-lo — que é como a
 * coleta acabaria conseguindo quebrar a landing.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Uso", description = "Coleta de acesso, sem cookie de rastreio e sem guardar IP")
public class UsageController {

    private final UsageCollector usage;

    public UsageController(UsageCollector usage) {
        this.usage = usage;
    }

    @PostMapping("/telemetria/evento")
    @Operation(
            summary = "Registra um acesso a uma rota do app",
            description =
                    "O servidor deriva dispositivo, navegador, sistema, idioma e país da própria"
                            + " requisição e ignora qualquer um desses campos no corpo. O endereço"
                            + " de IP é usado para derivar país e compor a chave de visita, e é"
                            + " descartado — não há coluna de IP em tabela nenhuma"
                            + " (docs/11-privacidade.md).")
    public ResponseEntity<Void> evento(
            HttpServletRequest request,
            @Valid @RequestBody(required = false) UsageDtos.EventRequest body) {
        usage.pageView(request, body);
        return ResponseEntity.noContent().build();
    }
}
