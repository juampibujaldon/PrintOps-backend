package com.printops.demo.dto;

import java.time.LocalDate;
import java.util.List;

// Rentabilidad por impresora en un período: ingresos de presupuestos aceptados/cobrados
// menos su costo de producción (filamento, energía, mano de obra, diseño) y el costo de
// mantenimiento (repuestos + mano de obra de órdenes completadas).
public record ProfitabilityDTO(LocalDate from, LocalDate to, Totals totals, List<Row> printers) {

    public record Row(Long printerId, String printerName, int quotes, double hoursSold, double revenue,
                      double productionCost, double energyCost, double maintenanceCost, double profit,
                      Double marginPercent, double profitPerHour) {
    }

    public record Totals(int quotes, double revenue, double productionCost, double maintenanceCost, double profit,
                         Double marginPercent) {
    }
}
