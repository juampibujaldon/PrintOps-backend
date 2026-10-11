// src/main/java/com/printops/demo/entity/NotificationPreference.java
package com.printops.demo.entity;

import jakarta.persistence.*;

// Preferencias de notificación por usuario (1:1 con User).
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "maintenance_due", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private boolean maintenanceDue = true;

    @Column(name = "low_stock", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private boolean lowStock = true;

    @Column(name = "assigned_orders", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private boolean assignedOrders = true;

    @Column(name = "orders_in_review", nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private boolean ordersInReview = true;

    @Column(name = "daily_summary", nullable = false, columnDefinition = "BOOLEAN DEFAULT false")
    private boolean dailySummary = false;

    // El default vive en Java: un DEFAULT en columnDefinition rompe el ALTER de ddl-auto=update.
    @Column(name = "daily_summary_time", length = 5)
    private String dailySummaryTime = "09:00";

    public NotificationPreference() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public boolean isMaintenanceDue() { return maintenanceDue; }
    public void setMaintenanceDue(boolean v) { this.maintenanceDue = v; }
    public boolean isLowStock() { return lowStock; }
    public void setLowStock(boolean v) { this.lowStock = v; }
    public boolean isAssignedOrders() { return assignedOrders; }
    public void setAssignedOrders(boolean v) { this.assignedOrders = v; }
    public boolean isOrdersInReview() { return ordersInReview; }
    public void setOrdersInReview(boolean v) { this.ordersInReview = v; }
    public boolean isDailySummary() { return dailySummary; }
    public void setDailySummary(boolean v) { this.dailySummary = v; }
    public String getDailySummaryTime() { return dailySummaryTime; }
    public void setDailySummaryTime(String v) { this.dailySummaryTime = v; }
}
