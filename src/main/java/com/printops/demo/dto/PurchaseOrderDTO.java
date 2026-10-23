package com.printops.demo.dto;

import java.time.Instant;
import java.util.List;

public record PurchaseOrderDTO(Long id, String number, String status, Long supplierId, String supplierName,
                               String supplierEmail, String notes, double total, Instant createdAt, Instant sentAt,
                               Instant receivedAt, List<Line> lines) {

    public record Line(Long id, Long partId, String partName, String partNumber, int quantity, Double unitCost,
                       int currentStock, int minStock) {
    }
}
