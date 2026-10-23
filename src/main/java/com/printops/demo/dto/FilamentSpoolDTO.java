package com.printops.demo.dto;

import java.time.Instant;

public record FilamentSpoolDTO(Long id, String material, String color, String brand, double initialGrams,
                               double remainingGrams, double costPerKg, double pricePerGram, double remainingPercent,
                               boolean low, Long printerId, String printerName, boolean archived, Instant createdAt) {
}
