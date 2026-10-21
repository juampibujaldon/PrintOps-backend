package com.printops.demo.exception;

public class GoogleNotConfiguredException extends RuntimeException {
    public GoogleNotConfiguredException() {
        super("El inicio de sesión con Google no está configurado en el servidor.");
    }
}
