// src/main/java/com/printops/demo/dto/CreateOrderRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

// DTO de entrada para crear una orden de mantenimiento (US-04 / US-10).
public record CreateOrderRequest(
        @NotNull(message = "printerId es obligatorio")
        Long printerId,

        @NotBlank(message = "type es obligatorio")
        @Pattern(regexp = "PREVENTIVE|CORRECTIVE|CALIBRATION",
                 message = "type debe ser PREVENTIVE, CORRECTIVE o CALIBRATION")
        String type,

        String description,

        Integer estimatedTimeMinutes,

        List<ChecklistItemInput> checklistItems,

        List<PartInput> parts
) {
    public record ChecklistItemInput(
            @NotBlank(message = "text es obligatorio") String text,
            boolean done,
            boolean na
    ) {}

    // Pieza usada. Si partId es null → pieza externa (externalPart*).
    public record PartInput(
            Long partId,
            @NotNull(message = "quantity es obligatorio") Integer quantity,
            String externalPartName,
            String externalPartNumber,
            Double externalUnitPrice
    ) {}
}
