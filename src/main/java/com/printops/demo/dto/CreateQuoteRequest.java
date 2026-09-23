// src/main/java/com/printops/demo/dto/CreateQuoteRequest.java
package com.printops.demo.dto;

import com.printops.demo.entity.FilamentType;
import jakarta.validation.constraints.*;

// DTO de entrada para crear/guardar un presupuesto (US-11).
public record CreateQuoteRequest(
        @NotNull(message = "filamentGrams es obligatorio")
        @Positive(message = "filamentGrams debe ser mayor a 0")
        Double filamentGrams,

        @NotNull(message = "filamentPricePerGram es obligatorio")
        @Positive(message = "filamentPricePerGram debe ser mayor a 0")
        Double filamentPricePerGram,

        @NotNull(message = "filamentType es obligatorio")
        FilamentType filamentType,

        @NotNull(message = "printerWatts es obligatorio")
        @Positive(message = "printerWatts debe ser mayor a 0")
        Double printerWatts,

        @NotNull(message = "energyPriceKwh es obligatorio")
        @Positive(message = "energyPriceKwh debe ser mayor a 0")
        Double energyPriceKwh,

        @NotNull(message = "printingHours es obligatorio")
        @Positive(message = "printingHours debe ser mayor a 0")
        Double printingHours,

        @PositiveOrZero(message = "designHours no puede ser negativo")
        Double designHours,

        @PositiveOrZero(message = "designHourlyRate no puede ser negativo")
        Double designHourlyRate,

        @PositiveOrZero(message = "operatorHourlyRate no puede ser negativo")
        Double operatorHourlyRate,

        @NotNull(message = "marginPercent es obligatorio")
        @Min(value = 1, message = "marginPercent debe ser al menos 1")
        @Max(value = 100, message = "marginPercent no puede superar 100")
        Double marginPercent,

        @Min(value = 0, message = "discountPercent no puede ser negativo")
        @Max(value = 50, message = "discountPercent no puede superar 50")
        Double discountPercent,

        @Min(value = 1, message = "units debe ser al menos 1")
        Integer units,

        Long printerId,

        String clientName,

        String jobDescription
) {}
