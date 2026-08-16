// src/main/java/com/printops/demo/dto/PartResponse.java
package com.printops.demo.dto;

public record PartResponse(
        Long id,
        String name,
        String partNumber,
        int stockQuantity
) {}
