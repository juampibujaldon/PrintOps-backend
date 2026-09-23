// src/main/java/com/printops/demo/dto/PartUsedDTO.java
package com.printops.demo.dto;

// Pieza usada dentro de una orden del historial (US-historial).
public record PartUsedDTO(
        String partName,
        int quantity,
        double unitCost
) {}
