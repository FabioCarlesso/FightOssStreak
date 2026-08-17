package dev.fos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração da aplicação sob o prefixo {@code fos}.
 *
 * @param disclaimerVersion versão vigente do texto de disclaimer; subir força novo aceite
 * @param curriculum origem do currículo versionado
 */
@ConfigurationProperties(prefix = "fos")
public record FosProperties(String disclaimerVersion, Curriculum curriculum) {

    /**
     * @param index classpath do índice de módulos
     * @param syncOnStartup reingerir o currículo na subida da aplicação
     */
    public record Curriculum(String index, boolean syncOnStartup) {
        public Curriculum {
            if (index == null || index.isBlank()) {
                index = "classpath:curriculum/modules.json";
            }
        }
    }
}
