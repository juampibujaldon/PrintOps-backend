// src/main/java/com/printops/demo/dto/PrinterComponentDTO.java
package com.printops.demo.dto;

// Estado de un repuesto de desgaste de la impresora. Para la boquilla, la vida
// útil depende del filamento: lifespanHours es la de PLA/PETG (referencia).
public record PrinterComponentDTO(
        String component,
        String label,
        String quality,
        Double customLifespanHours,
        double standardLifespanHours,
        double lifespanHours,
        boolean filamentDependent,
        double hoursUsed,
        double hoursRemaining,
        double percent,
        java.time.Instant replacedAt
) {}
