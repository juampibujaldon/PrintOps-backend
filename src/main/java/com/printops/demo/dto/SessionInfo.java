// src/main/java/com/printops/demo/dto/SessionInfo.java
package com.printops.demo.dto;

import java.time.Instant;

public record SessionInfo(
    String deviceId,
    Instant createdAt,
    Instant expiresAt
) {}
