package com.printops.demo.dto;

import java.time.Instant;

// token solo viene al crear/rotar (se muestra una vez); después solo el hint.
public record IntegrationDTO(Long printerId, String type, String tokenHint, Instant createdAt, Instant lastSeenAt,
                             boolean online, String token) {
}
