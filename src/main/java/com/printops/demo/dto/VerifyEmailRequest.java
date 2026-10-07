package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(
        @NotBlank(message = "El código de verificación es obligatorio")
        String token
) {}
