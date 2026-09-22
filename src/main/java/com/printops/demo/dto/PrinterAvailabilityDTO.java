// src/main/java/com/printops/demo/dto/PrinterAvailabilityDTO.java
package com.printops.demo.dto;

// Disponibilidad de una impresora en el período (US-08).
public record PrinterAvailabilityDTO(
        Long id,
        String name,
        double availabilityPercent
) {}
