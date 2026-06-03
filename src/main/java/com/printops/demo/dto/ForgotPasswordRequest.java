// src/main/java/com/printops/demo/dto/ForgotPasswordRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
    @NotBlank @Email String email
) {}
