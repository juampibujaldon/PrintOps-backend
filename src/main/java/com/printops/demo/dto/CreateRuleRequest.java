// src/main/java/com/printops/demo/dto/CreateRuleRequest.java
package com.printops.demo.dto;

import com.printops.demo.entity.OrderType;
import com.printops.demo.entity.TriggerType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

// DTO de entrada para crear una regla de mantenimiento preventivo (US-06).
public record CreateRuleRequest(
        @NotNull(message = "printerId es obligatorio")
        Long printerId,

        @NotNull(message = "triggerType es obligatorio")
        TriggerType triggerType,

        @NotNull(message = "triggerValue es obligatorio")
        @Positive(message = "triggerValue debe ser mayor a 0")
        Integer triggerValue,

        // Días de alerta previa. Si viene null se usa el default 7.
        Integer alertDaysBefore,

        @NotNull(message = "maintenanceType es obligatorio")
        OrderType maintenanceType,

        // Ítems del checklist que se copiarán a la orden generada.
        List<String> checklistItems
) {}
