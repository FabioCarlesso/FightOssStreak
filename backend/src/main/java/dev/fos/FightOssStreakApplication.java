package dev.fos;

import dev.fos.config.FosProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FosProperties.class)
public class FightOssStreakApplication {

    public static void main(String[] args) {
        SpringApplication.run(FightOssStreakApplication.class, args);
    }
}
