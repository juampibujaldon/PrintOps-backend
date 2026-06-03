// src/main/java/com/printops/demo/dto/LoginRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank(message = "El email es requerido")
    @Email(message = "Email inválido")
    String email,

    @NotBlank(message = "La contraseña es requerida")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    String password,

    @NotBlank(message = "El deviceId es requerido")
    String deviceId,

    boolean rememberMe
) {}
