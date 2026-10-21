package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
    @NotBlank(message = "El idToken es requerido")
    String idToken,

    @NotBlank(message = "El deviceId es requerido")
    String deviceId,

    boolean rememberMe
) {}
