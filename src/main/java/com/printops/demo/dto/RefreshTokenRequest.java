// src/main/java/com/printops/demo/dto/RefreshTokenRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;

// refreshToken es opcional: los navegadores lo mandan en la cookie httpOnly.
public record RefreshTokenRequest(
    String refreshToken,
    @NotBlank String deviceId
) {}
