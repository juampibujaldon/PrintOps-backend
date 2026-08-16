// src/main/java/com/printops/demo/entity/PrinterStatus.java
package com.printops.demo.entity;

// Estados normalizados en inglés para alinearse con el @Pattern del DTO
// (FIX 1): OPERATIVE | MAINTENANCE | OUT_OF_SERVICE.
public enum PrinterStatus {
    OPERATIVE,
    MAINTENANCE,
    OUT_OF_SERVICE
}
