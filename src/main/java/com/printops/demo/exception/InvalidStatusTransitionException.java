// src/main/java/com/printops/demo/exception/InvalidStatusTransitionException.java
package com.printops.demo.exception;

// Se lanza cuando la transición de estado pedida no existe en la máquina de
// estados (US-05). Se traduce a HTTP 400 en GlobalExceptionHandler.
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
