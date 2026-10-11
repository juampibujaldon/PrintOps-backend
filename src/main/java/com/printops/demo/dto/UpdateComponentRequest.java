// src/main/java/com/printops/demo/dto/UpdateComponentRequest.java
package com.printops.demo.dto;

import com.printops.demo.entity.ComponentQuality;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateComponentRequest(
        @NotNull(message = "La calidad es obligatoria")
        ComponentQuality quality,
        // Obligatorio solo con quality = CUSTOM.
        @Positive(message = "La vida útil debe ser mayor a 0")
        @DecimalMax(value = "100000", message = "La vida útil no puede superar 100.000 horas")
        Double customLifespanHours
) {}
