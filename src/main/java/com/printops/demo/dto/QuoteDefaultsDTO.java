// src/main/java/com/printops/demo/dto/QuoteDefaultsDTO.java
package com.printops.demo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QuoteDefaultsDTO(
        @NotNull @DecimalMin("0") Double defaultEnergyPriceKwh,
        @NotNull @DecimalMin("0") Double defaultOperatorHourlyRate,
        @DecimalMin("0") Double defaultDesignHourlyRate,
        @NotNull @DecimalMin("0") Double defaultMarginPercent,
        @NotNull @Min(1) Integer quoteValidityDays
) {}
