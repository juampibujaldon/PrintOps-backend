// src/main/java/com/printops/demo/dto/AddPartRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotNull;

// DTO para agregar una pieza usada a una orden (US-10). Si partId es null →
// pieza externa (externalPart*).
public record AddPartRequest(
        Long partId,
        @NotNull(message = "quantity es obligatorio") Integer quantity,
        String externalPartName,
        String externalPartNumber,
        Double externalUnitPrice
) {}
