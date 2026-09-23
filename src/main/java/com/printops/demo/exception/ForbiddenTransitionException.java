// src/main/java/com/printops/demo/exception/ForbiddenTransitionException.java
package com.printops.demo.exception;

// Se lanza cuando la transición existe pero el usuario no tiene el rol (o la
// asignación) necesaria para ejecutarla (US-05). Se traduce a HTTP 403.
public class ForbiddenTransitionException extends RuntimeException {

    public ForbiddenTransitionException(String message) {
        super(message);
    }
}
