// src/main/java/com/printops/demo/dto/NotificationPreferenceDTO.java
package com.printops.demo.dto;

public record NotificationPreferenceDTO(
        boolean maintenanceDue,
        boolean lowStock,
        boolean assignedOrders,
        boolean ordersInReview,
        boolean dailySummary,
        String dailySummaryTime
) {}
