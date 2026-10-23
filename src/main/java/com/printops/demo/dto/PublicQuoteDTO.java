package com.printops.demo.dto;

import java.time.Instant;
import java.time.LocalDate;

// Lo que ve el cliente en el link público: sin costos internos, márgenes ni datos de otros.
public record PublicQuoteDTO(String quoteNumber, String status, String clientName, String jobDescription,
                             int units, double unitPrice, double discountPercent, double totalPrice, String currency,
                             LocalDate validUntil, boolean expired, Instant clientDecisionAt,
                             Workshop workshop) {

    public record Workshop(String name, String email, String phone, String address, String logoUrl) {
    }
}
