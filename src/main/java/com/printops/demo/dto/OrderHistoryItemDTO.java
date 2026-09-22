// src/main/java/com/printops/demo/dto/OrderHistoryItemDTO.java
package com.printops.demo.dto;

import java.time.Instant;
import java.util.List;

// Ítem del historial de mantenimiento (US-07): una orden resumida para el timeline.
public record OrderHistoryItemDTO(
        Long id,
        String orderNumber,
        String type,
        String status,
        String description,
        Instant createdAt,
        Instant closedAt,
        Integer actualMinutes,
        String technicianName,
        List<PartUsedDTO> partsUsed,
        List<String> photoUrls,
        double partsCost
) {
    // Pieza usada en la orden con su costo (US-07).
    public record PartUsedDTO(
            Long partId,
            String partName,
            String partNumber,
            int quantity,
            double cost
    ) {}
}
