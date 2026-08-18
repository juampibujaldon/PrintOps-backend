// src/main/java/com/printops/demo/dto/StatusChangeRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// DTO para cambiar el estado de una orden (US-05).
public record StatusChangeRequest(
        @NotBlank(message = "newStatus es obligatorio")
        @Pattern(regexp = "PENDING|IN_PROGRESS|IN_REVIEW|COMPLETED|CANCELLED",
                 message = "newStatus debe ser PENDING, IN_PROGRESS, IN_REVIEW, COMPLETED o CANCELLED")
        String newStatus,

        // Obligatorio solo cuando se rechaza (IN_REVIEW -> IN_PROGRESS); se valida en el servicio.
        String comment
) {}
