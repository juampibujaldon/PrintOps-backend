// src/main/java/com/printops/demo/dto/NextMaintenanceDateRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

// DTO para actualizar la fecha del próximo mantenimiento (FIX 4).
public record NextMaintenanceDateRequest(
        @NotNull(message = "must not be null")
        LocalDate nextMaintenanceDate
) {}
