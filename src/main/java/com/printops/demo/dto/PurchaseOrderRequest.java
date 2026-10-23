package com.printops.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record PurchaseOrderRequest(Long supplierId, String notes,
                                   @NotEmpty(message = "Agregá al menos un repuesto") @Valid List<LineInput> lines) {

    public record LineInput(@NotNull(message = "Repuesto obligatorio") Long partId,
                       @NotNull @Min(value = 1, message = "La cantidad debe ser al menos 1") Integer quantity,
                       @PositiveOrZero(message = "El costo no puede ser negativo") Double unitCost) {
    }
}
