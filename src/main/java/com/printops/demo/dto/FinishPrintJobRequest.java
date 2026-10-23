package com.printops.demo.dto;

import jakarta.validation.constraints.PositiveOrZero;

// Valores reales opcionales: si faltan se usan los estimados del trabajo.
public record FinishPrintJobRequest(
        @PositiveOrZero(message = "Las horas no pueden ser negativas") Double actualHours,
        @PositiveOrZero(message = "Los gramos no pueden ser negativos") Double actualGrams,
        String notes) {
}
