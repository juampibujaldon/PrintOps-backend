// src/main/java/com/printops/demo/dto/MetricsFilters.java
package com.printops.demo.dto;

import java.time.LocalDate;

// Filtro de período del dashboard (US-08). from/to nunca null: el controller
// aplica defaults (inicio del mes actual / hoy).
public record MetricsFilters(
        LocalDate from,
        LocalDate to
) {}
