// src/main/java/com/printops/demo/entity/ComponentType.java
package com.printops.demo.entity;

// Componentes de desgaste que se estiman en los presupuestos.
// La vida útil base (calidad estándar) está en PrinterComponentService.
public enum ComponentType {
    NOZZLE,
    HOTEND,
    BELTS,
    BEARINGS,
    HEATBED
}
