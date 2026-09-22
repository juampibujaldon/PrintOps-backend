// src/main/java/com/printops/demo/dto/MonthlyCostDTO.java
package com.printops.demo.dto;

// Costo agregado de un mes (US-08). month en formato "YYYY-MM".
public record MonthlyCostDTO(
        String month,
        double cost
) {}
