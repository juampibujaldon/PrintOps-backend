package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleCompleteRequest(
    @NotBlank(message = "El onboardingToken es requerido")
    String onboardingToken,

    @NotBlank(message = "El deviceId es requerido")
    String deviceId,

    boolean rememberMe,
    String inviteToken,
    String workspaceName
) {}
