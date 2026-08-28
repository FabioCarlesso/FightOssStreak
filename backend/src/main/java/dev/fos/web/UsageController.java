package dev.fos.web;

import dev.fos.config.FosProperties;
import dev.fos.service.UsageCollector;
import dev.fos.web.dto.UsageDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 *
 * <p><b>Com uma exceção, e ela é o ponto de {@code fos.usage.enabled}:</b> ambiente com a coleta
 * desligada responde <b>503 {@code coleta_desligada}</b>. Sem isso, "desligado" só significaria
 * "não grava" — o navegador continuaria mandando uma requisição por navegação, para sempre, para um
 * servidor que as joga fora. Com o código, o cliente para de mandar pelo resto da visita. É o mesmo
 * 503 do cadastro sem provedor de envio (D38): funcionalidade ausente neste ambiente se anuncia,
 * não falha em silêncio. O <b>código</b> importa — 503 sozinho é o que o proxy devolve enquanto o
 * backend reinicia, e aquele não pode desligar coleta nenhuma.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Uso", description = "Coleta de acesso, sem cookie de rastreio e sem guardar IP")
public class UsageController {

    /** Código do 503 — é por ele que o cliente distingue "desligado" de "backend reiniciando". */
    static final String COLETA_DESLIGADA = "coleta_desligada";

    private final UsageCollector usage;
    private final FosProperties.Usage config;

    public UsageController(UsageCollector usage, FosProperties properties) {
        this.usage = usage;
        this.config = properties.usage();
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
    public ResponseEntity<?> evento(
            HttpServletRequest request,
            @Valid @RequestBody(required = false) UsageDtos.EventRequest body) {
        if (!config.enabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(
                            new UsageDtos.ColetaDesligada(
                                    COLETA_DESLIGADA,
                                    "A coleta de uso está desligada neste ambiente."));
        }
        usage.pageView(request, body);
        return ResponseEntity.noContent().build();
    }
}
