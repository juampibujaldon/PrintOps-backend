// src/main/java/com/printops/demo/service/PrinterComponentService.java
package com.printops.demo.service;

import com.printops.demo.dto.PrinterComponentDTO;
import com.printops.demo.entity.*;
import com.printops.demo.repository.PrinterComponentRepository;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

// Vida útil de los repuestos de desgaste por impresora: calidad del repuesto
// (escala la vida útil base) y horas desde que se instaló (se resetea al reemplazarlo).
@Service
public class PrinterComponentService {

    // Vida útil base (calidad estándar), en horas de impresión.
    private static final double HOTEND_HOURS = 800.0;
    private static final double BELTS_HOURS = 1500.0;
    private static final double BEARINGS_HOURS = 2000.0;
    private static final double HEATBED_HOURS = 3000.0;

    private final PrinterComponentRepository componentRepository;
    private final PrinterRepository printerRepository;

    public PrinterComponentService(PrinterComponentRepository componentRepository,
                                   PrinterRepository printerRepository) {
        this.componentRepository = componentRepository;
        this.printerRepository = printerRepository;
    }

    // ── API ──────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<PrinterComponentDTO> list(Long printerId) {
        Printer printer = findPrinter(printerId);
        List<PrinterComponent> saved = componentRepository.findByPrinterId(printerId);
        return Arrays.stream(ComponentType.values())
                .map(type -> toDto(printer, saved.stream()
                        .filter(c -> c.getComponent() == type).findFirst()
                        .orElseGet(() -> defaults(printerId, type))))
                .toList();
    }

    @Transactional
    public PrinterComponentDTO update(Long printerId, ComponentType type, ComponentQuality quality, Double customHours) {
        Printer printer = findPrinter(printerId);
        if (quality == ComponentQuality.CUSTOM && (customHours == null || customHours <= 0)) {
            throw new IllegalArgumentException("Indicá la vida útil en horas para una calidad personalizada.");
        }
        PrinterComponent c = getOrCreate(printerId, type);
        c.setQuality(quality);
        c.setCustomLifespanHours(quality == ComponentQuality.CUSTOM ? customHours : null);
        // Con otra vida útil, la alerta del 90% puede volver a corresponder.
        c.setWearAlertSent(false);
        return toDto(printer, componentRepository.save(c));
    }

    // El repuesto se cambió: su contador arranca en las horas actuales de la impresora.
    @Transactional
    public PrinterComponentDTO replace(Long printerId, ComponentType type) {
        Printer printer = findPrinter(printerId);
        PrinterComponent c = getOrCreate(printerId, type);
        c.setInstalledAtHours(totalHours(printer));
        c.setReplacedAt(Instant.now());
        c.setWearAlertSent(false);
        c.setOpenOrderId(null);
        return toDto(printer, componentRepository.save(c));
    }

    // ── Usado por el cálculo de desgaste de los presupuestos ────────────────
    public double lifespanHours(PrinterComponent c, FilamentType filament) {
        if (c.getQuality() == ComponentQuality.CUSTOM && c.getCustomLifespanHours() != null) {
            return c.getCustomLifespanHours();
        }
        return standardLifespan(c.getComponent(), filament) * c.getQuality().getMultiplier();
    }

    public double hoursSinceInstall(PrinterComponent c, Printer printer) {
        double installed = c.getInstalledAtHours() != null ? c.getInstalledAtHours() : 0.0;
        return Math.max(0.0, totalHours(printer) - installed);
    }

    // Componente persistido (lo crea si no existe) para guardar alertas/órdenes.
    @Transactional
    public PrinterComponent getOrCreate(Long printerId, ComponentType type) {
        return componentRepository.findByPrinterIdAndComponent(printerId, type)
                .orElseGet(() -> componentRepository.save(defaults(printerId, type)));
    }

    // Sin impresora (presupuesto sin máquina asignada): calidad estándar, desde 0 h.
    public PrinterComponent forPrinterOrDefault(Printer printer, ComponentType type) {
        if (printer == null || printer.getId() == null) return defaults(null, type);
        return componentRepository.findByPrinterIdAndComponent(printer.getId(), type)
                .orElseGet(() -> defaults(printer.getId(), type));
    }

    public static String label(ComponentType type) {
        return switch (type) {
            case NOZZLE -> "Boquilla (nozzle)";
            case HOTEND -> "Hotend";
            case BELTS -> "Correas";
            case BEARINGS -> "Rodamientos";
            case HEATBED -> "Cama caliente";
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    static double standardLifespan(ComponentType type, FilamentType filament) {
        return switch (type) {
            // La boquilla se gasta más rápido con filamentos abrasivos o flexibles.
            case NOZZLE -> switch (filament != null ? filament : FilamentType.PLA) {
                case PLA, PETG -> 500.0;
                case ABS, ASA -> 400.0;
                case TPU -> 300.0;
                case OTHER -> 200.0;
            };
            case HOTEND -> HOTEND_HOURS;
            case BELTS -> BELTS_HOURS;
            case BEARINGS -> BEARINGS_HOURS;
            case HEATBED -> HEATBED_HOURS;
        };
    }

    private PrinterComponentDTO toDto(Printer printer, PrinterComponent c) {
        double lifespan = lifespanHours(c, FilamentType.PLA);
        double used = hoursSinceInstall(c, printer);
        return new PrinterComponentDTO(
                c.getComponent().name(),
                label(c.getComponent()),
                c.getQuality().name(),
                c.getCustomLifespanHours(),
                standardLifespan(c.getComponent(), FilamentType.PLA),
                round2(lifespan),
                c.getComponent() == ComponentType.NOZZLE && c.getQuality() != ComponentQuality.CUSTOM,
                round2(used),
                round2(Math.max(0.0, lifespan - used)),
                round2(lifespan > 0 ? used / lifespan * 100.0 : 0.0),
                c.getReplacedAt());
    }

    private PrinterComponent defaults(Long printerId, ComponentType type) {
        PrinterComponent c = new PrinterComponent();
        c.setPrinterId(printerId);
        c.setComponent(type);
        c.setQuality(ComponentQuality.STANDARD);
        c.setInstalledAtHours(0.0);
        return c;
    }

    private Printer findPrinter(Long printerId) {
        return printerRepository.findByIdAndWorkspaceId(printerId, TenantContext.getCurrentWorkspaceId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con id " + printerId));
    }

    private double totalHours(Printer printer) {
        return printer != null && printer.getTotalPrintingHours() != null ? printer.getTotalPrintingHours() : 0.0;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
