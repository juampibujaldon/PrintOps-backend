// src/main/java/com/printops/demo/exception/InsufficientStockException.java
package com.printops.demo.exception;

// Lanzada al cerrar una orden cuando no hay stock suficiente (US-10). Se mapea a 400.
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
