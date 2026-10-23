package com.printops.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SupplierDTO(Long id,
                          @NotBlank(message = "El nombre es obligatorio") String name,
                          @Email(message = "Email inválido") String email,
                          String phone, String website, String notes) {
}
