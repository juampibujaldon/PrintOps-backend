// src/main/java/com/printops/demo/dto/FailingPrinterDTO.java
package com.printops.demo.dto;

// Impresora con más fallas correctivas en el período (US-08).
public record FailingPrinterDTO(
        Long id,
        String name,
        int correctiveCount,
        double totalCost
) {}
