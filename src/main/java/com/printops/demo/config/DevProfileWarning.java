// src/main/java/com/printops/demo/config/DevProfileWarning.java
package com.printops.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// Aviso visible al arrancar con el perfil "dev": usa credenciales locales de
// application-dev.yaml, que nunca deben llegar a producción.
@Component
@Profile("dev")
public class DevProfileWarning {

    private static final Logger log = LoggerFactory.getLogger(DevProfileWarning.class);

    @EventListener(ApplicationReadyEvent.class)
    public void warn() {
        log.warn("Perfil DEV activo: credenciales de desarrollo (application-dev.yaml). "
                + "En producción usá SPRING_PROFILES_ACTIVE=prod con DB_PASSWORD y JWT_SECRET.");
    }
}
