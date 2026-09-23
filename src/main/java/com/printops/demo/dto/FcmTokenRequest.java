// src/main/java/com/printops/demo/dto/FcmTokenRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;

// Body del endpoint POST /api/auth/fcm-token (US-05).
// El frontend registra acá el token de Firebase Cloud Messaging del dispositivo.
public record FcmTokenRequest(
        @NotBlank(message = "token es obligatorio")
        String token
) {}
