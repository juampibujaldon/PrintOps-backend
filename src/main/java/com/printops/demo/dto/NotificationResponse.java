// src/main/java/com/printops/demo/dto/NotificationResponse.java
package com.printops.demo.dto;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        String message,
        Long orderId,
        Long recipientUserId,
        String type,
        boolean read,
        Instant createdAt
) {}
