// src/main/java/com/printops/demo/dto/StockUpdateRequest.java
package com.printops.demo.dto;

import com.printops.demo.entity.MovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// DTO para ajustar el stock de una pieza manualmente (US-10).
// PURCHASE/RETURN → suma la cantidad; ADJUSTMENT → fija el stock a la cantidad.
public record StockUpdateRequest(
        @NotNull(message = "type es obligatorio")
        MovementType type,

        @NotNull(message = "quantity es obligatorio")
        @Positive(message = "quantity debe ser mayor a 0")
        Integer quantity,

        String note
) {}
