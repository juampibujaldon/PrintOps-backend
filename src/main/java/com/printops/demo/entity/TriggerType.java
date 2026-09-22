// src/main/java/com/printops/demo/entity/TriggerType.java
package com.printops.demo.entity;

// Criterio que dispara una regla de mantenimiento preventivo (US-06).
// TIME_BASED      -> cada N días calendario.
// USAGE_HOURS     -> cada N horas de uso de la impresora.
// FILAMENT_GRAMS  -> cada N gramos de filamento consumidos.
public enum TriggerType {
    TIME_BASED,
    USAGE_HOURS,
    FILAMENT_GRAMS
}
