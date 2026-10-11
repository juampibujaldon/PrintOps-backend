// src/main/java/com/printops/demo/config/QuoteSchemaFix.java
package com.printops.demo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Hibernate creó un CHECK (status IN ('DRAFT','SENT','ACCEPTED','REJECTED')) en
// quotes y ddl-auto=update no lo actualiza al agregar valores al enum, así que
// guardar PAID fallaba. Se elimina: el valor ya lo valida el enum en Java.
// Es idempotente (IF EXISTS), se puede ejecutar en cada arranque.
@Component
public class QuoteSchemaFix {

    private static final Logger log = LoggerFactory.getLogger(QuoteSchemaFix.class);

    private final JdbcTemplate jdbc;

    public QuoteSchemaFix(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dropOutdatedStatusCheck() {
        try {
            jdbc.execute("ALTER TABLE IF EXISTS quotes DROP CONSTRAINT IF EXISTS quotes_status_check");
        } catch (Exception e) {
            log.warn("No se pudo eliminar quotes_status_check: {}", e.getMessage());
        }
    }
}
