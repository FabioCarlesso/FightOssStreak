package dev.fos.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * O que o web manda na coleta de uso (#84, D50).
 *
 * <p>Repare no tamanho da lista. Ela é curta porque <b>o servidor não confia no cliente</b>: tudo
 * que dá para derivar da própria requisição — dispositivo, navegador, sistema, idioma, país, e o
 * tipo de todo evento de funil — é derivado, e um corpo que traga esses campos é simplesmente
 * ignorado. O que sobra aqui é o que só o navegador sabe: em que rota ele está, de que site veio e
 * com que campanha.
 */
public final class UsageDtos {

    private UsageDtos() {}

    /**
     * @param caminho rota do app. Normalizado contra a lista de rotas conhecidas antes de virar
     *     linha — o que não bate vira {@code /outro}, e nenhum segmento variável é gravado
     * @param referrer host de onde a pessoa veio, sem caminho e sem query. Só o host: caminho de
     *     terceiro é conteúdo de navegação alheia
     * @param utmSource origem da campanha
     * @param utmMedium meio da campanha
     * @param utmCampaign nome da campanha
     */
    @Schema(description = "Uma mudança de rota no app. Nada aqui identifica quem navega.")
    public record EventRequest(
            @Size(max = 300) String caminho,
            @Size(max = 300) String referrer,
            @Size(max = 200) String utmSource,
            @Size(max = 200) String utmMedium,
            @Size(max = 200) String utmCampaign) {}
}
