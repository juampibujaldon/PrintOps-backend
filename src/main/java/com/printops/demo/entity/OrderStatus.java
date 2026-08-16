// src/main/java/com/printops/demo/entity/OrderStatus.java
package com.printops.demo.entity;

// Ciclo de vida de la orden (US-04). Siempre se crea en PENDING.
public enum OrderStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
