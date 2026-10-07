// src/main/java/com/printops/demo/dto/WorkspaceResponse.java
package com.printops.demo.dto;

public record WorkspaceResponse(
        Long id,
        String name,
        String phone,
        String contactEmail,
        String address,
        String logoUrl,
        String currency,
        Double defaultEnergyPriceKwh,
        Double defaultOperatorHourlyRate,
        Double defaultDesignHourlyRate,
        Double defaultMarginPercent,
        Integer quoteValidityDays
) {}
