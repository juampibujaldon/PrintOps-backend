// src/main/java/com/printops/demo/dto/StatusHistoryResponse.java
package com.printops.demo.dto;

import java.time.Instant;

public record StatusHistoryResponse(
        Long id,
        Long orderId,
        String fromStatus,
        String toStatus,
        String comment,
        Long changedById,
        String changedByName,
        Instant changedAt
) {}
