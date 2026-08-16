package dev.fos.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "Hoje" é injetado, nunca lido de {@code LocalDate.now()} espalhado pelo código.
 *
 * <p>Streak e SRS são inteiramente definidos por datas: sem um relógio injetável, testar "o que
 * acontece depois de três dias" exigiria esperar três dias.
 */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
