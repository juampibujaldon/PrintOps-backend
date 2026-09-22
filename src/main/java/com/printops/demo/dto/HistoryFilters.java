// src/main/java/com/printops/demo/dto/HistoryFilters.java
package com.printops.demo.dto;

import com.printops.demo.entity.OrderType;

import java.time.LocalDate;

// Filtros del historial de mantenimiento (US-07).
// type == null => todos los tipos; from/to == null => sin filtro de fechas.
public record HistoryFilters(
        OrderType type,
        LocalDate from,
        LocalDate to,
        int page,
        int size
) {}
