// src/main/java/com/printops/demo/exception/EmailNotVerifiedException.java
package com.printops.demo.exception;

// Se lanza cuando un usuario intenta iniciar sesión sin haber verificado su email.
public class EmailNotVerifiedException extends RuntimeException {
    public EmailNotVerifiedException(String message) {
        super(message);
    }
}
