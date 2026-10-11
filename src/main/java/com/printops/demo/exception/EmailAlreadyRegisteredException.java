// src/main/java/com/printops/demo/exception/EmailAlreadyRegisteredException.java
package com.printops.demo.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
