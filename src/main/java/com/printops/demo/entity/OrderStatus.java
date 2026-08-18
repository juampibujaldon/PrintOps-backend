// src/main/java/com/printops/demo/entity/OrderStatus.java
package com.printops.demo.entity;

// Ciclo de vida de la orden (US-05).
// Transiciones válidas:
//   PENDING -> IN_PROGRESS   (técnico asignado)
//   IN_PROGRESS -> IN_REVIEW (técnico asignado)
//   IN_REVIEW -> COMPLETED   (manager)
//   IN_REVIEW -> IN_PROGRESS (manager, rechazo con comentario)
//   PENDING/IN_PROGRESS/IN_REVIEW -> CANCELLED (cancelación)
public enum OrderStatus {
    PENDING,
    IN_PROGRESS,
    IN_REVIEW,
    COMPLETED,
    CANCELLED
}
