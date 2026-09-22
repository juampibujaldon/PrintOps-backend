// src/main/java/com/printops/demo/dto/PartRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// DTO para crear/actualizar una pieza del catálogo (US-04).
public record PartRequest(
        @NotBlank(message = "name es obligatorio")
        String name,

        @NotBlank(message = "partNumber es obligatorio")
        String partNumber,

        @NotNull(message = "stockQuantity es obligatorio")
        @Min(value = 0, message = "stockQuantity no puede ser negativo")
        Integer stockQuantity,

        // Precio unitario opcional (US-07). Default 0.0 si no se envía.
        Double unitPrice,

        // Stock mínimo opcional (US-08). Default 5 si no se envía.
        Integer minStock
) {}
