// src/main/java/com/printops/demo/dto/PrinterMetricsDTO.java
package com.printops.demo.dto;

// Métricas agregadas de mantenimiento de una impresora (US-07).
public record PrinterMetricsDTO(
        int totalInterventions,
        double totalCost,
        int preventiveCount,
        int correctiveCount,
        int calibrationCount,
        // Tiempo medio entre fallas (días). Null si no hay suficientes correctivas.
        Double mtbfDays,
        String mostReplacedPartName,
        int mostReplacedPartCount
) {}
