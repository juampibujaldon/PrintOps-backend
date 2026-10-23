package com.printops.demo.service;

import com.printops.demo.dto.ComponentWearDTO;
import com.printops.demo.entity.*;
import com.printops.demo.repository.MaintenanceOrderRepository;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.repository.StatusHistoryRepository;
import com.printops.demo.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Único punto que suma uso real a una impresora (horas y gramos de filamento) y evalúa
// el desgaste de sus componentes. Lo llaman los trabajos de impresión al terminar y la
// telemetría del agente. Antes el uso se sumaba al sacar un presupuesto de borrador,
// aunque después se rechazara y nunca se imprimiera.
@Service
public class PrinterUsageService {

    private static final String[] NAMES = {"boquilla", "hotend", "correas", "rodamientos", "cama caliente"};
    private static final ComponentType[] TYPES = {
            ComponentType.NOZZLE, ComponentType.HOTEND, ComponentType.BELTS, ComponentType.BEARINGS, ComponentType.HEATBED};

    private final PrinterRepository printerRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final StatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final PrinterComponentService componentService;
    private final WearCalculator wearCalculator;
    private final NotificationService notificationService;
    private final DashboardCache dashboardCache;

    public PrinterUsageService(PrinterRepository printerRepository, MaintenanceOrderRepository orderRepository,
                               StatusHistoryRepository historyRepository, UserRepository userRepository,
                               PrinterComponentService componentService, WearCalculator wearCalculator,
                               NotificationService notificationService, DashboardCache dashboardCache) {
        this.printerRepository = printerRepository;
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.componentService = componentService;
        this.wearCalculator = wearCalculator;
        this.notificationService = notificationService;
        this.dashboardCache = dashboardCache;
    }

    // actor: quien terminó el trabajo (recibe alertas y la orden correctiva); null si vino
    // de la telemetría, en cuyo caso se avisa a los supervisores.
    @Transactional
    public void recordUsage(Printer printer, double hours, double grams, FilamentType filament,
                            User actor, String source) {
        if (printer == null || (hours <= 0 && grams <= 0)) return;
        ComponentWearDTO wear = wearCalculator.calculate(Math.max(0, hours), filament, printer);

        printer.setTotalPrintingHours(nz(printer.getTotalPrintingHours()) + Math.max(0, hours));
        printer.setTotalFilamentGrams(nz(printer.getTotalFilamentGrams()) + Math.max(0, grams));
        printerRepository.save(printer);

        handleWearAlerts(printer, wear, actor, source);
    }

    // Una alerta por repuesto (al pasar el 90%) y una sola orden correctiva abierta por
    // repuesto (al pasar el 100%). Se vuelven a habilitar al reemplazar el repuesto.
    private void handleWearAlerts(Printer printer, ComponentWearDTO wear, User actor, String source) {
        ComponentWearDTO.ComponentStatus[] comps = {
                wear.nozzle(), wear.hotend(), wear.belts(), wear.bearings(), wear.heatbed()};
        for (int i = 0; i < comps.length; i++) {
            ComponentWearDTO.ComponentStatus status = comps[i];
            if (status.percent() < 90.0) continue;
            PrinterComponent component = componentService.getOrCreate(printer.getId(), TYPES[i]);

            if (status.percent() > 100.0) {
                if (!hasOpenOrder(component.getOpenOrderId())) {
                    component.setOpenOrderId(createCorrectiveOrder(printer, NAMES[i], actor, source));
                }
            } else if (!component.isWearAlertSent()) {
                for (Long recipient : recipients(printer, actor)) {
                    notificationService.createFor(recipient, "WEAR_ALERT",
                            "La " + NAMES[i] + " de la impresora " + printer.getSerialNumber()
                                    + " superó el " + (int) status.percent() + "% de vida útil (" + source + ").", null);
                }
            }
            component.setWearAlertSent(true);
        }
    }

    private List<Long> recipients(Printer printer, User actor) {
        if (actor != null) return List.of(actor.getId());
        return userRepository.findByRoleAndWorkspaceId(Role.MANAGER, printer.getWorkspaceId()).stream()
                .map(User::getId).toList();
    }

    private boolean hasOpenOrder(Long orderId) {
        if (orderId == null) return false;
        return orderRepository.findById(orderId)
                .map(o -> o.getStatus() != OrderStatus.COMPLETED && o.getStatus() != OrderStatus.CANCELLED)
                .orElse(false);
    }

    private Long createCorrectiveOrder(Printer printer, String componentName, User actor, String source) {
        MaintenanceOrder order = new MaintenanceOrder();
        order.setPrinter(printer);
        order.setAssignedTo(actor != null && actor.getRole() == Role.TECNICO ? actor : null);
        order.setType(OrderType.CORRECTIVE);
        order.setStatus(OrderStatus.PENDING);
        order.setWorkspaceId(printer.getWorkspaceId());
        order.setDescription("Mantenimiento correctivo automático: " + componentName
                + " superó el 100% de vida útil (" + source + ").");
        orderRepository.save(order);
        dashboardCache.invalidate(); // la orden nueva cuenta en el dashboard (tras el commit)

        StatusHistory h = new StatusHistory();
        h.setOrderId(order.getId());
        h.setToStatus(OrderStatus.PENDING);
        h.setChangedBy(actor);
        historyRepository.save(h);

        for (Long recipient : recipients(printer, actor)) {
            notificationService.createFor(recipient, "ORDER_CREATED",
                    "Orden correctiva #" + order.getId() + " creada automáticamente por desgaste de "
                            + componentName + " en " + printer.getSerialNumber() + ".", order.getId());
        }
        return order.getId();
    }

    private static double nz(Double d) {
        return d != null ? d : 0.0;
    }
}
