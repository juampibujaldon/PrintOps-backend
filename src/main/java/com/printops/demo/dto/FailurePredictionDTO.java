package com.printops.demo.dto;

import java.time.LocalDate;

// Predicción de la próxima falla por impresora a partir de su historial de correctivas.
// riskPercent: días desde la última falla sobre el intervalo medio entre fallas (MTBF).
public record FailurePredictionDTO(Long printerId, String printerName, String status, int correctiveCount,
                                   Double mtbfDays, LocalDate lastFailure, LocalDate predictedNextFailure,
                                   Double riskPercent, String worstComponent, Double worstComponentWear,
                                   boolean hasOpenPreventive, String recommendation) {
}
