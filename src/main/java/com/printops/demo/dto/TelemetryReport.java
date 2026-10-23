package com.printops.demo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// Lo que manda el agente local en cada ciclo. `usage` solo viaja cuando termina un trabajo
// (horas y gramos realmente consumidos); el resto es el estado actual.
public record TelemetryReport(
        @NotBlank String state,
        @DecimalMin("0") @DecimalMax("100") Double progress,
        Double nozzleTemp,
        Double bedTemp,
        @Size(max = 255) String jobName,
        @PositiveOrZero Integer remainingSeconds,
        @Size(max = 255) String message,
        @Valid Usage usage) {

    public record Usage(@PositiveOrZero Double printSeconds, @PositiveOrZero Double filamentGrams) {
    }
}
