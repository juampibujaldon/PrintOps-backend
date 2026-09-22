// src/main/java/com/printops/demo/dto/PartUsageSummaryDTO.java
package com.printops.demo.dto;

// Uso agregado de una pieza en el historial de una impresora (US-07).
public record PartUsageSummaryDTO(
        Long partId,
        String partName,
        String partNumber,
        int totalQuantityUsed,
        double totalCost
) {}
