// src/main/java/com/printops/demo/dto/PrinterCostDTO.java
package com.printops.demo.dto;

// Costo total (piezas + mano de obra) de una impresora en el período (US-08).
public record PrinterCostDTO(
        Long id,
        String name,
        double totalCost
) {}
