// src/main/java/com/printops/demo/dto/MaintenanceRuleDTO.java
package com.printops.demo.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// DTO de salida de una regla (US-06). Además de los campos de la entidad expone
// el progreso calculado y la estimación del próximo disparo para la UI.
public record MaintenanceRuleDTO(
        Long id,
        Long printerId,
        String printerName,
        String triggerType,
        Integer triggerValue,
        Integer alertDaysBefore,
        String maintenanceType,
        List<String> checklistItems,
        Boolean active,
        LocalDate lastTriggeredAt,
        Long createdById,
        Instant createdAt,

        // % del umbral ya recorrido (0..100). Calculado por el servicio.
        Double progressPercent,

        // Fecha estimada del próximo trigger (solo TIME_BASED; null en los demás).
        LocalDate nextTriggerEstimate,

        // Valor actual consumido desde el último reset:
        //   USAGE_HOURS    -> horas de uso acumuladas desde el snapshot.
        //   FILAMENT_GRAMS -> gramos consumidos desde el snapshot.
        //   TIME_BASED     -> null.
        Double currentValue,

        // Contadores actuales de la impresora (para referencia de la UI).
        Double totalPrintingHours,
        Double totalFilamentGrams
) {}
