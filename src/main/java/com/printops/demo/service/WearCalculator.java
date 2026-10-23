package com.printops.demo.service;

import com.printops.demo.dto.ComponentWearDTO;
import com.printops.demo.entity.ComponentType;
import com.printops.demo.entity.FilamentType;
import com.printops.demo.entity.Printer;
import com.printops.demo.entity.PrinterComponent;
import org.springframework.stereotype.Component;

// Desgaste de cada componente = horas desde que se instaló el repuesto + horas de un
// trabajo, contra su vida útil (según la calidad del repuesto y, la boquilla, el filamento).
@Component
public class WearCalculator {

    private final PrinterComponentService componentService;

    public WearCalculator(PrinterComponentService componentService) {
        this.componentService = componentService;
    }

    public ComponentWearDTO calculate(double jobHours, FilamentType type, Printer printer) {
        return new ComponentWearDTO(
                status(printer, ComponentType.NOZZLE, type, jobHours),
                status(printer, ComponentType.HOTEND, type, jobHours),
                status(printer, ComponentType.BELTS, type, jobHours),
                status(printer, ComponentType.BEARINGS, type, jobHours),
                status(printer, ComponentType.HEATBED, type, jobHours));
    }

    private ComponentWearDTO.ComponentStatus status(Printer printer, ComponentType component,
                                                    FilamentType filament, double jobHours) {
        PrinterComponent c = componentService.forPrinterOrDefault(printer, component);
        double lifespan = componentService.lifespanHours(c, filament);
        double hoursAfter = componentService.hoursSinceInstall(c, printer) + jobHours;
        double percent = (hoursAfter / lifespan) * 100.0;
        double remaining = Math.max(0.0, lifespan - hoursAfter);
        return new ComponentWearDTO.ComponentStatus(round2(percent), round2(hoursAfter), round2(remaining), percent >= 90.0);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
