// src/main/java/com/printops/demo/dto/QuoteResultsDTO.java
package com.printops.demo.dto;

// Resultado del cálculo de un presupuesto (US-11).
public record QuoteResultsDTO(
        double filamentCost,
        double energyCost,
        double designCost,
        double operatorCost,
        double totalCost,
        double unitPrice,
        double totalPrice,
        double totalProfit,
        double roi,
        ComponentWearDTO componentWear
) {}
