package com.printops.demo.dto;

import java.time.Instant;

public record PrintJobDTO(Long id, String title, String status, int priority, int units, double estimatedHours,
                          double filamentGrams, String filamentType, Double actualHours, Double actualGrams,
                          String notes, Long printerId, String printerName, Long spoolId, String spoolLabel,
                          Long quoteId, String quoteNumber, String clientName, Instant createdAt,
                          Instant startedAt, Instant finishedAt) {
}
