// src/main/java/com/printops/demo/dto/AddPartRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotNull;

// DTO para agregar una pieza usada a una orden (US-04).
public record AddPartRequest(
        Long partId,
        String partNumber,
        @NotNull(message = "quantity es obligatorio") Integer quantity,
        boolean external
) {}
