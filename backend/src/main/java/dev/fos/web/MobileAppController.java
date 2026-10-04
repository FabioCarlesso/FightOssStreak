package dev.fos.web;

import dev.fos.config.FosProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O que o app mobile pergunta antes de qualquer outra coisa (#139).
 *
 * <p>Na web, todo deploy é a versão de todo mundo. No celular não: a versão antiga fica meses no
 * aparelho, e mudar um contrato da API passa a quebrar quem não atualizou. A versão mínima é o
 * jeito de a API dizer "esta versão não serve mais" em vez de responder coisa que o app não
 * entende.
 */
@RestController
@RequestMapping("/api/app")
@Tag(name = "App mobile", description = "Login do app Android e iOS, por token")
public class MobileAppController {

    private final FosProperties.Mobile mobile;

    public MobileAppController(FosProperties properties) {
        this.mobile = properties.mobile();
    }

    /**
     * @param minimumVersion a menor versão do app que a API atende, no formato {@code 1.2.3}. Nula
     *     quando qualquer versão serve
     */
    public record AppVersionView(String minimumVersion) {}

    @GetMapping("/versao")
    @Operation(
            summary = "Versão mínima do app",
            description =
                    "Pública e sem segredo: responde antes do login, para o app velho saber que"
                            + " precisa atualizar antes de tentar entrar. É o app que compara.")
    public AppVersionView versao() {
        return new AppVersionView(mobile.minVersion());
    }
}
