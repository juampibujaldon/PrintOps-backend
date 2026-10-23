package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// Gasto extra de un presupuesto ("Rodamiento" → 4500). Se cobra a costo: suma al total sin
// margen ni descuento.
public record ExtraCostItem(
        @NotBlank(message = "El concepto del gasto es obligatorio") @Size(max = 255) String description,
        @NotNull(message = "El importe del gasto es obligatorio") @PositiveOrZero(message = "El importe no puede ser negativo") Double amount) {
}
