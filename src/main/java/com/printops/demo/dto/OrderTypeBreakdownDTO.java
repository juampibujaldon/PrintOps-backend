// src/main/java/com/printops/demo/dto/OrderTypeBreakdownDTO.java
package com.printops.demo.dto;

// Desglose de órdenes por tipo (US-08).
public record OrderTypeBreakdownDTO(
        int preventive,
        int corrective,
        int calibration
) {}
