// src/main/java/com/printops/demo/entity/QuoteStatus.java
package com.printops.demo.entity;

// Ciclo de vida del presupuesto (US-11).
// Transiciones válidas: DRAFT -> SENT -> ACCEPTED | REJECTED.
public enum QuoteStatus {
    DRAFT,
    SENT,
    ACCEPTED,
    REJECTED
}
