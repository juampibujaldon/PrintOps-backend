// src/main/java/com/printops/demo/dto/RefreshTokenRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
    @NotBlank String refreshToken,
    @NotBlank String deviceId
) {}
