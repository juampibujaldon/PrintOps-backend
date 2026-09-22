// src/main/java/com/printops/demo/dto/CreateSparePartRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

// DTO de entrada para crear/actualizar un repuesto (US-10).
public record CreateSparePartRequest(
        @NotBlank(message = "name es obligatorio")
        String name,

        @NotBlank(message = "partNumber es obligatorio")
        String partNumber,

        String description,
        String brand,
        String category,

        @NotNull(message = "stock es obligatorio")
        @PositiveOrZero(message = "stock no puede ser negativo")
        Integer stock,

        @NotNull(message = "minStock es obligatorio")
        @Positive(message = "minStock debe ser mayor a 0")
        Integer minStock,

        @NotNull(message = "unitPrice es obligatorio")
        @Positive(message = "unitPrice debe ser mayor a 0")
        Double unitPrice,

        String supplierUrl
) {}
