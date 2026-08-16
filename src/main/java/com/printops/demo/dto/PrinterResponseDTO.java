// src/main/java/com/printops/demo/dto/PrinterResponseDTO.java
package com.printops.demo.dto;

import java.time.LocalDate;

// DTO de salida (FIX 2): lo único que se devuelve al cliente.
// qrCodeData se expone como String (Base64/uuid), nunca como bytes.
public record PrinterResponseDTO(
        Long id,
        String name,
        String brand,
        String model,
        String serialNumber,
        LocalDate purchaseDate,
        String status,
        String location,
        LocalDate nextMaintenanceDate,
        String photoUrl,
        String qrCodeData
) {}
