// src/main/java/com/printops/demo/dto/OrderResponseDTO.java
package com.printops.demo.dto;

import java.time.Instant;
import java.util.List;

// DTO de salida de una orden (US-04).
public record OrderResponseDTO(
        Long id,
        Long printerId,
        String type,
        String status,
        String description,
        Integer estimatedTimeMinutes,
        Integer actualTimeMinutes,
        Instant createdAt,
        List<ChecklistItemResponse> checklistItems,
        List<PartResponse> parts,
        List<PhotoResponse> photos
) {
    public record ChecklistItemResponse(Long id, String text, boolean done, boolean na) {}

    public record PartResponse(
            Long id,
            Long partId,
            String partNumber,
            String partName,
            int quantity,
            boolean external
    ) {}

    public record PhotoResponse(Long id, String url, String label) {}
}
