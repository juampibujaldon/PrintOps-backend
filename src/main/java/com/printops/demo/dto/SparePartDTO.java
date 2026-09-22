// src/main/java/com/printops/demo/dto/SparePartDTO.java
package com.printops.demo.dto;

// DTO de salida de un repuesto (US-10).
public record SparePartDTO(
        Long id,
        String name,
        String partNumber,
        String description,
        String brand,
        String category,
        int stock,
        int minStock,
        double unitPrice,
        String supplierUrl,
        boolean isLowStock,     // stock <= minStock
        boolean isOutOfStock    // stock == 0
) {}
