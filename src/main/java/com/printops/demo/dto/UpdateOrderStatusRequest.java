// src/main/java/com/printops/demo/dto/UpdateOrderStatusRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// DTO para cambiar el estado de una orden (US-04).
public record UpdateOrderStatusRequest(
        @NotBlank(message = "status es obligatorio")
        @Pattern(regexp = "PENDING|IN_PROGRESS|COMPLETED|CANCELLED",
                 message = "status debe ser PENDING, IN_PROGRESS, COMPLETED o CANCELLED")
        String status,

        // Tiempo real en minutos (se registra al cerrar la orden).
        Integer actualTimeMinutes
) {}
