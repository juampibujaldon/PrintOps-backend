// src/main/java/com/printops/demo/dto/StockMovementDTO.java
package com.printops.demo.dto;

import java.time.Instant;

// DTO de salida de un movimiento de stock (US-10).
public record StockMovementDTO(
        Long id,
        String type,
        int quantityBefore,
        int quantityChange,
        int quantityAfter,
        String note,
        String performedByName,
        Instant performedAt,
        String orderNumber   // nullable
) {}
