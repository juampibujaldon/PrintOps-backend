package com.printops.demo.support;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

// Cada contexto de tests arranca de una base vacía migrada con las mismas migraciones de
// producción: valida los scripts de Flyway y aísla las corridas entre sí.
@TestConfiguration(proxyBeanMethods = false)
public class TestFlywayConfig {

    @Bean
    FlywayMigrationStrategy cleanMigrate() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
