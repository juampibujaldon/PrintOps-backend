package com.printops.demo.dto;

import com.printops.demo.entity.FilamentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record SpoolRequest(
        @NotNull(message = "El material es obligatorio") FilamentType material,
        String color,
        String brand,
        @NotNull(message = "Los gramos iniciales son obligatorios") @Positive(message = "Los gramos deben ser mayores a 0") Double initialGrams,
        @PositiveOrZero(message = "Los gramos restantes no pueden ser negativos") Double remainingGrams,
        @NotNull(message = "El costo por kg es obligatorio") @PositiveOrZero(message = "El costo no puede ser negativo") Double costPerKg,
        Long printerId) {
}
