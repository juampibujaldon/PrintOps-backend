// src/main/java/com/printops/demo/dto/LowStockPartDTO.java
package com.printops.demo.dto;

// Pieza con stock bajo (stock <= minStock) para el dashboard (US-08).
public record LowStockPartDTO(
        Long id,
        String name,
        String partNumber,
        int stock,
        int minStock
) {}
