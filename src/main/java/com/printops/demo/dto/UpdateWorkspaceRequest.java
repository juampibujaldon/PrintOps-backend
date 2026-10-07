// src/main/java/com/printops/demo/dto/UpdateWorkspaceRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateWorkspaceRequest(
        @NotBlank String name,
        String phone,
        String contactEmail,
        String address,
        String logoUrl,
        String currency
) {}
