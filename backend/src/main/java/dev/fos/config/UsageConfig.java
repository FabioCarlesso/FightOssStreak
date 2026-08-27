package dev.fos.config;

import dev.fos.service.GeoIpDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A base de geolocalização, carregada uma vez na subida (#84, D50).
 *
 * <p>Bean e não {@code @Component}: quem constrói precisa do caminho configurado, e a construção
 * pode acabar na base vazia — que é o caso normal em dev e no CI. Ver {@link GeoIpDatabase}.
 */
@Configuration
class UsageConfig {

    @Bean
    GeoIpDatabase geoIpDatabase(FosProperties properties) {
        return GeoIpDatabase.load(properties.usage().geoipDatabase());
    }
}
