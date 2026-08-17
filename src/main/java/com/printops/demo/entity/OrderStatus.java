// src/main/java/com/printops/demo/entity/OrderStatus.java
package com.printops.demo.entity;

// Ciclo de vida de la orden (US-04 + revisión).
// Siempre se crea en PENDING. El técnico la avanza hasta IN_REVIEW y el
// manager la aprueba (APPROVED) o rechaza (REJECTED).
public enum OrderStatus {
    PENDING,
    IN_PROGRESS,
    IN_REVIEW,
    APPROVED,
    REJECTED,
    CANCELLED
}
