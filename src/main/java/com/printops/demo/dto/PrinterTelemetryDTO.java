package com.printops.demo.dto;

import java.time.Instant;

// Estado en vivo de una impresora. online=false si el agente no reporta hace más de 2 minutos.
public record PrinterTelemetryDTO(Long printerId, String state, Double progress, Double nozzleTemp, Double bedTemp,
                                  String jobName, Integer remainingSeconds, String message, Instant updatedAt,
                                  boolean online) {
}
