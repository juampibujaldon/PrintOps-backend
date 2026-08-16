// src/main/java/com/printops/demo/dto/CreateOrderRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

// DTO de entrada para crear una orden de mantenimiento (US-04).
public record CreateOrderRequest(
        @NotNull(message = "printerId es obligatorio")
        Long printerId,

        @NotBlank(message = "type es obligatorio")
        @Pattern(regexp = "PREVENTIVE|CORRECTIVE|CALIBRATION",
                 message = "type debe ser PREVENTIVE, CORRECTIVE o CALIBRATION")
        String type,

        // Descripción del problema/trabajo. Obligatoria para órdenes correctivas
        // (se valida en el servicio).
        String description,

        // Tiempo estimado en minutos (lo ingresa el técnico al crear).
        Integer estimatedTimeMinutes,

        List<ChecklistItemInput> checklistItems,

        List<PartInput> parts
) {
    public record ChecklistItemInput(
            @NotBlank(message = "text es obligatorio") String text,
            boolean done,
            boolean na
    ) {}

    public record PartInput(
            Long partId,
            String partNumber,
            @NotNull(message = "quantity es obligatorio") Integer quantity,
            boolean external
    ) {}
}
