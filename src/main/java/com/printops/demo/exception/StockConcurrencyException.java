// src/main/java/com/printops/demo/exception/StockConcurrencyException.java
package com.printops.demo.exception;

// Lanzada cuando una actualización de stock falla por concurrencia (FIX 4).
// Se mapea a 409 Conflict para que el frontend avise al usuario.
public class StockConcurrencyException extends RuntimeException {

    public StockConcurrencyException(String message) {
        super(message);
    }
}
