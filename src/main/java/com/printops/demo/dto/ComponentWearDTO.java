// src/main/java/com/printops/demo/dto/ComponentWearDTO.java
package com.printops.demo.dto;

// Desgaste proyectado de los componentes de una impresora (US-11).
public record ComponentWearDTO(
        ComponentStatus nozzle,
        ComponentStatus hotend,
        ComponentStatus belts,
        ComponentStatus bearings,
        ComponentStatus heatbed
) {
    public record ComponentStatus(
            double percent,
            double hoursUsed,
            double hoursRemaining,
            boolean critical
    ) {}
}
