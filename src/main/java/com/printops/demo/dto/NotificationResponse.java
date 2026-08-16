// src/main/java/com/printops/demo/dto/NotificationResponse.java
package com.printops.demo.dto;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String message,
        Long orderId,
        boolean read,
        Instant createdAt
) {}
