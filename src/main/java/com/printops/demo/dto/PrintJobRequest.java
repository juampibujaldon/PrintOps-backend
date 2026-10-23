package com.printops.demo.dto;

import com.printops.demo.entity.FilamentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record PrintJobRequest(
        @NotBlank(message = "El título es obligatorio") String title,
        Integer priority,
        @Min(value = 1, message = "units debe ser al menos 1") Integer units,
        @PositiveOrZero(message = "Las horas no pueden ser negativas") Double estimatedHours,
        @PositiveOrZero(message = "Los gramos no pueden ser negativos") Double filamentGrams,
        FilamentType filamentType,
        Long printerId,
        Long spoolId,
        String notes) {
}
