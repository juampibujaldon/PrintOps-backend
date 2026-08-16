// src/main/java/com/printops/demo/entity/Notification.java
package com.printops.demo.entity;

import jakarta.persistence.*;

import java.time.Instant;

// Notificación simple (US-04): se crea, por ejemplo, al dar de alta una orden
// para que un supervisor/admin pueda enterarse.
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String message;

    // Orden relacionada (opcional).
    @Column
    private Long orderId;

    @Column(nullable = false)
    private boolean read;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public Notification() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public Instant getCreatedAt() { return createdAt; }
}
