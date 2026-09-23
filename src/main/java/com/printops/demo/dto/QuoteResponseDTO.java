// src/main/java/com/printops/demo/dto/QuoteResponseDTO.java
package com.printops.demo.dto;

import java.time.LocalDateTime;

// DTO de salida completo de un presupuesto (US-11).
public record QuoteResponseDTO(
        Long id,
        String quoteNumber,
        String clientName,
        String jobDescription,
        Integer units,
        Double filamentGrams,
        Double filamentPricePerGram,
        String filamentType,
        Double printerWatts,
        Double energyPriceKwh,
        Double printingHours,
        Double designHours,
        Double designHourlyRate,
        Double operatorHourlyRate,
        Double marginPercent,
        Double discountPercent,
        Double filamentCost,
        Double energyCost,
        Double designCost,
        Double operatorCost,
        Double totalCost,
        Double unitPrice,
        Double totalPrice,
        Double totalProfit,
        Double roi,
        ComponentWearDTO componentWear,
        Long printerId,
        String printerName,
        String status,
        LocalDateTime createdAt,
        String pdfUrl
) {}
