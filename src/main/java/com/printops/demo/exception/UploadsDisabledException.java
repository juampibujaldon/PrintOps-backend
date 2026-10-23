package com.printops.demo.exception;

public class UploadsDisabledException extends RuntimeException {
    public UploadsDisabledException() {
        super("La subida de imágenes está deshabilitada en este servidor.");
    }
}
