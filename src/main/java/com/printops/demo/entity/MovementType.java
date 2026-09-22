// src/main/java/com/printops/demo/entity/MovementType.java
package com.printops.demo.entity;

// Tipo de movimiento de stock (US-10).
public enum MovementType {
    PURCHASE,   // compra (entrada)
    ORDER_USE,  // consumo en una orden (salida)
    ADJUSTMENT, // ajuste manual (fija el stock a un valor)
    RETURN      // devolución (entrada)
}
