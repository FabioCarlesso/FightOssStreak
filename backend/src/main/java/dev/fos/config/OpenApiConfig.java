package dev.fos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O spec OpenAPI não é documentação decorativa: é a fonte a partir da qual {@code shared/types} é
 * gerado (docs/01-stack-tecnica.md). Sem ele, os tipos do front divergem do backend em duas
 * semanas.
 */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI fosOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("FightOssStreak API")
                                .version("0.1.0")
                                .description(
                                        """
                        API de currículo, progresso, streak e repetição espaçada.

                        AVISO: o FightOssStreak é ferramenta de organização e revisão de estudos. \
                        Não ensina jiu-jitsu e não substitui instrução presencial com professor \
                        qualificado.""")
                                .license(new License().name("MIT")));
    }
}
