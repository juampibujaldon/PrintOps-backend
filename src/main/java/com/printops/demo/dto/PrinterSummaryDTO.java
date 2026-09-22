// src/main/java/com/printops/demo/dto/PrinterSummaryDTO.java
package com.printops.demo.dto;

// Resumen de la impresora incluido en la respuesta del historial (US-07).
public record PrinterSummaryDTO(
        Long id,
        String name,
        String brand,
        String model,
        String serialNumber
) {}
