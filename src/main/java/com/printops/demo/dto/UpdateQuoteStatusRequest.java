// src/main/java/com/printops/demo/dto/UpdateQuoteStatusRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateQuoteStatusRequest(
        @NotBlank(message = "status es obligatorio")
        @Pattern(regexp = "DRAFT|SENT|ACCEPTED|REJECTED|PAID",
                 message = "status debe ser DRAFT, SENT, ACCEPTED, REJECTED o PAID")
        String status
) {}
