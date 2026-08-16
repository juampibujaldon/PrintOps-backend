// src/main/java/com/printops/demo/dto/CreatePrinterRequest.java
package com.printops.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

// DTO de entrada para el alta de impresoras (FIX 1 y FIX 2).
// Solo expone los campos que el cliente puede enviar; el id y el qrCodeData
// se asignan en el servidor y nunca provienen del request.
public record CreatePrinterRequest(
        @NotBlank(message = "must not be blank")
        String brand,

        @NotBlank(message = "must not be blank")
        String model,

        @NotBlank(message = "must not be blank")
        String serialNumber,

        @NotNull(message = "must not be null")
        LocalDate purchaseDate,

        @NotBlank(message = "must not be blank")
        @Pattern(regexp = "OPERATIVE|MAINTENANCE|OUT_OF_SERVICE",
                 message = "must match OPERATIVE, MAINTENANCE or OUT_OF_SERVICE")
        String status,

        // Nombre opcional de la impresora.
        String name,

        // Ubicación (FIX 3): opcional al momento del alta.
        String location,

        // Próximo mantenimiento (FIX 4): opcional al momento del alta.
        LocalDate nextMaintenanceDate
) {}
