// src/main/java/com/printops/demo/dto/PrinterHistoryDTO.java
package com.printops.demo.dto;

import java.util.List;

// Respuesta del historial de mantenimiento (US-07): impresora + métricas + página de órdenes.
public record PrinterHistoryDTO(
        PrinterSummaryDTO printer,
        PrinterMetricsDTO metrics,
        List<OrderHistoryItemDTO> orders,
        int totalPages,
        int currentPage
) {}
