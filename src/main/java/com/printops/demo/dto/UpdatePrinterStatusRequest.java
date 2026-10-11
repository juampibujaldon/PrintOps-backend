// src/main/java/com/printops/demo/dto/UpdatePrinterStatusRequest.java
package com.printops.demo.dto;

import com.printops.demo.entity.PrinterStatus;
import jakarta.validation.constraints.NotNull;

// DTO para cambiar el estado de una impresora (Operativa / Mantenimiento / Fuera de servicio).
public record UpdatePrinterStatusRequest(
        @NotNull(message = "El estado es obligatorio")
        PrinterStatus status
) {}
