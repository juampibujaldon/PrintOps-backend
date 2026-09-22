// src/main/java/com/printops/demo/dto/DashboardMetricsDTO.java
package com.printops.demo.dto;

import java.time.LocalDate;
import java.util.List;

// Respuesta del dashboard de métricas globales (US-08).
public record DashboardMetricsDTO(
        // Disponibilidad
        double globalAvailabilityPercent,
        List<PrinterAvailabilityDTO> availabilityByPrinter,

        // Costos
        double totalCost,
        double totalPartsCost,
        double totalLaborCost,
        List<MonthlyCostDTO> costByMonth,        // últimos 6 meses
        List<PrinterCostDTO> costByPrinter,

        // Órdenes
        int totalOrders,
        int openOrders,
        int closedOrders,
        OrderTypeBreakdownDTO byType,
        double avgResolutionHours,
        int overdueOrders,

        // Top fallas
        List<FailingPrinterDTO> top3FailingPrinters,

        // Stock
        List<LowStockPartDTO> lowStockParts,

        // Metadata
        LocalDate periodFrom,
        LocalDate periodTo
) {}
