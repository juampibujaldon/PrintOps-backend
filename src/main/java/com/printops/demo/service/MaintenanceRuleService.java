// src/main/java/com/printops/demo/service/MaintenanceRuleService.java
package com.printops.demo.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.printops.demo.dto.CreateRuleRequest;
import com.printops.demo.dto.MaintenanceRuleDTO;
import com.printops.demo.entity.*;
import com.printops.demo.repository.MaintenanceOrderRepository;
import com.printops.demo.repository.MaintenanceRuleRepository;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.repository.UserRepository;
import com.printops.demo.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

// Reglas de mantenimiento preventivo (US-06). Evalúa condiciones periódicas
// (tiempo / horas de uso / gramos de filamento) y auto-genera órdenes.
@Service
public class MaintenanceRuleService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceRuleService.class);

    // Estados que consideramos "orden abierta" (no permiten regenerar).
    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(OrderStatus.PENDING, OrderStatus.IN_PROGRESS, OrderStatus.IN_REVIEW);

    private final MaintenanceRuleRepository ruleRepository;
    private final PrinterRepository printerRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // FIX 7: umbral (%) para alerta previa en reglas por horas/gramos.
    @Value("${app.maintenance.alert-threshold-percent:10.0}")
    private double alertThresholdPercent;

    // Jackson 3 (Spring Boot 4). Instancia propia para serializar el checklist.
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public MaintenanceRuleService(MaintenanceRuleRepository ruleRepository,
                                  PrinterRepository printerRepository,
                                  MaintenanceOrderRepository orderRepository,
                                  UserRepository userRepository,
                                  NotificationService notificationService) {
        this.ruleRepository = ruleRepository;
        this.printerRepository = printerRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    @Transactional
    public MaintenanceRuleDTO createRule(CreateRuleRequest req, User user) {
        Printer printer = printerRepository.findByIdAndWorkspaceId(req.printerId(), wsId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con id " + req.printerId()));

        // Una sola regla activa por triggerType e impresora para no duplicar órdenes.
        if (ruleRepository.existsByPrinterIdAndTriggerTypeAndActiveTrue(printer.getId(), req.triggerType())) {
            throw new IllegalArgumentException(
                    "Ya existe una regla activa de tipo " + req.triggerType() + " para esta impresora.");
        }

        MaintenanceRule rule = new MaintenanceRule();
        rule.setPrinter(printer);
        rule.setTriggerType(req.triggerType());
        rule.setTriggerValue(req.triggerValue());
        rule.setAlertDaysBefore(req.alertDaysBefore() != null ? req.alertDaysBefore() : 7);
        rule.setMaintenanceType(req.maintenanceType());
        rule.setChecklistTemplate(serializeChecklist(req.checklistItems()));
        rule.setActive(true);
        rule.setWorkspaceId(wsId());
        rule.setCreatedBy(user);

        MaintenanceRule saved = ruleRepository.save(rule);
        log.info("Regla creada id={} impresora={} tipo={} umbral={}",
                saved.getId(), printer.getSerialNumber(), req.triggerType(), req.triggerValue());
        return toDTO(saved);
    }

    // Llamado por el scheduler: recorre todas las reglas activas y evalúa cada una.
    @Transactional
    public void evaluateAllRules() {
        List<MaintenanceRule> rules = ruleRepository.findByActiveTrue();
        if (rules.isEmpty()) {
            return;
        }
        for (MaintenanceRule rule : rules) {
            try {
                evaluateRule(rule);
            } catch (Exception e) {
                // Una regla rota no debe frenar la evaluación del resto.
                log.error("Error evaluando regla id={}", rule.getId(), e);
            }
        }
    }

    private void evaluateRule(MaintenanceRule rule) {
        Printer printer = rule.getPrinter();
        boolean triggered = isTriggered(rule, printer);

        if (triggered) {
            boolean hasOpenOrder = orderRepository.existsByMaintenanceRuleIdAndStatusIn(
                    rule.getId(), OPEN_STATUSES);

            if (!hasOpenOrder) {
                MaintenanceOrder order = createOrder(rule, printer);
                updateSnapshotAfterTrigger(rule, printer);
                notifyTechnicians(
                        "Mantenimiento preventivo requerido para la impresora " + printerName(printer)
                                + " (regla #" + rule.getId() + "). Orden #" + order.getId() + " generada.",
                        order.getId());
                log.info("Regla id={} disparó orden id={}", rule.getId(), order.getId());
            }
            return;
        }

        // Alerta previa: tiempo (días) o progreso (horas/gramos).
        if (rule.getTriggerType() == TriggerType.TIME_BASED) {
            maybeSendPreAlert(rule, printer);
        } else if (rule.getTriggerType() == TriggerType.USAGE_HOURS
                || rule.getTriggerType() == TriggerType.FILAMENT_GRAMS) {
            maybeSendUsagePreAlert(rule, printer);
        }
    }

    // ── Condición de disparo ────────────────────────────────────────────────
    private boolean isTriggered(MaintenanceRule rule, Printer printer) {
        return switch (rule.getTriggerType()) {
            case TIME_BASED -> {
                LocalDate baseline = baselineDate(rule);
                yield !LocalDate.now().isBefore(baseline.plusDays(rule.getTriggerValue()));
            }
            case USAGE_HOURS -> hours(printer) >= rule.getLastUsageHoursSnapshot() + rule.getTriggerValue();
            case FILAMENT_GRAMS -> grams(printer) >= rule.getLastFilamentSnapshot() + rule.getTriggerValue();
        };
    }

    // ── Alerta previa (TIME_BASED): notifica si faltan <= alertDaysBefore días ──
    private void maybeSendPreAlert(MaintenanceRule rule, Printer printer) {
        int alertDays = rule.getAlertDaysBefore() != null ? rule.getAlertDaysBefore() : 7;
        LocalDate due = baselineDate(rule).plusDays(rule.getTriggerValue());
        long daysUntil = ChronoUnit.DAYS.between(LocalDate.now(), due);

        if (daysUntil < 0 || daysUntil > alertDays) {
            return;
        }
        // Evita reenviar la misma alerta todos los días.
        if (rule.getAlertSentAt() != null && rule.getAlertSentAt().isEqual(LocalDate.now())) {
            return;
        }
        notifyTechnicians(
                "Próximo mantenimiento de " + printerName(printer) + " en " + daysUntil + " día(s) (regla #" + rule.getId() + ").",
                null);
        rule.setAlertSentAt(LocalDate.now());
        ruleRepository.save(rule);
    }

    // FIX 7: alerta previa para reglas por horas/gramos (umbral porcentual).
    private void maybeSendUsagePreAlert(MaintenanceRule rule, Printer printer) {
        double progress = computeProgress(rule, printer);
        if (progress < (100.0 - alertThresholdPercent) || progress >= 100.0) {
            return;
        }
        // Evita reenviar la misma alerta todos los días.
        if (rule.getAlertSentAt() != null && rule.getAlertSentAt().isEqual(LocalDate.now())) {
            return;
        }
        String unit = rule.getTriggerType() == TriggerType.USAGE_HOURS ? "hs de uso" : "g de filamento";
        notifyTechnicians(
                "La impresora " + printerName(printer) + " está por alcanzar el umbral de mantenimiento "
                        + "(regla #" + rule.getId() + ", " + Math.round(progress) + "% de " + rule.getTriggerValue() + " " + unit + ").",
                null);
        rule.setAlertSentAt(LocalDate.now());
        ruleRepository.save(rule);
    }

    // ── Generación de la orden ──────────────────────────────────────────────
    private MaintenanceOrder createOrder(MaintenanceRule rule, Printer printer) {
        MaintenanceOrder order = new MaintenanceOrder();
        order.setPrinter(printer);
        order.setType(rule.getMaintenanceType() != null ? rule.getMaintenanceType() : OrderType.PREVENTIVE);
        order.setStatus(OrderStatus.PENDING);
        order.setDescription("Mantenimiento automático (" + rule.getTriggerType() + ") generado por la regla #" + rule.getId());
        order.setMaintenanceRuleId(rule.getId());
        order.setWorkspaceId(rule.getWorkspaceId());

        for (String item : parseChecklist(rule.getChecklistTemplate())) {
            OrderChecklistItem ci = new OrderChecklistItem();
            ci.setText(item);
            ci.setDone(false);
            ci.setNa(false);
            order.addChecklistItem(ci);
        }
        return orderRepository.save(order);
    }

    private void updateSnapshotAfterTrigger(MaintenanceRule rule, Printer printer) {
        rule.setLastTriggeredAt(LocalDate.now());
        if (rule.getTriggerType() == TriggerType.USAGE_HOURS) {
            rule.setLastUsageHoursSnapshot(hours(printer));
        } else if (rule.getTriggerType() == TriggerType.FILAMENT_GRAMS) {
            rule.setLastFilamentSnapshot(grams(printer));
        }
        rule.setAlertSentAt(null);
        ruleRepository.save(rule);
    }

    // ── Notificaciones push a todos los técnicos ─────────────────────────────
    private void notifyTechnicians(String message, Long orderId) {
        userRepository.findByRole(Role.TECNICO).forEach(tech ->
                notificationService.createFor(tech.getId(), "MAINTENANCE_RULE", message, orderId));
    }

    // ── Pausa / reactivación / borrado ───────────────────────────────────────
    @Transactional
    public MaintenanceRuleDTO pauseRule(Long id) {
        return setActive(id, false);
    }

    @Transactional
    public MaintenanceRuleDTO resumeRule(Long id) {
        return setActive(id, true);
    }

    private MaintenanceRuleDTO setActive(Long id, boolean active) {
        MaintenanceRule rule = ruleRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Regla no encontrada con id " + id));
        rule.setActive(active);
        return toDTO(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Long id) {
        MaintenanceRule rule = ruleRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Regla no encontrada con id " + id));
        ruleRepository.delete(rule);
    }

    // ── Consultas ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<MaintenanceRuleDTO> getRulesForPrinter(Long printerId) {
        // Valida que la impresora pertenezca al workspace del usuario.
        printerRepository.findByIdAndWorkspaceId(printerId, wsId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con id " + printerId));
        return ruleRepository.findByPrinterIdAndActiveTrue(printerId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaintenanceRuleDTO getRule(Long id) {
        return ruleRepository.findByIdAndWorkspaceId(id, wsId())
                .map(this::toDTO)
                .orElseThrow(() -> new NoSuchElementException("Regla no encontrada con id " + id));
    }

    // ── Mapeo a DTO con progreso calculado ───────────────────────────────────
    private MaintenanceRuleDTO toDTO(MaintenanceRule rule) {
        Printer printer = rule.getPrinter();
        return new MaintenanceRuleDTO(
                rule.getId(),
                printer != null ? printer.getId() : null,
                printerName(printer),
                rule.getTriggerType() != null ? rule.getTriggerType().name() : null,
                rule.getTriggerValue(),
                rule.getAlertDaysBefore(),
                rule.getMaintenanceType() != null ? rule.getMaintenanceType().name() : null,
                parseChecklist(rule.getChecklistTemplate()),
                rule.getActive(),
                rule.getLastTriggeredAt(),
                rule.getCreatedBy() != null ? rule.getCreatedBy().getId() : null,
                rule.getCreatedAt(),
                computeProgress(rule, printer),
                computeNextTrigger(rule),
                computeCurrentValue(rule, printer),
                printer != null ? printer.getTotalPrintingHours() : null,
                printer != null ? printer.getTotalFilamentGrams() : null
        );
    }

    private Double computeProgress(MaintenanceRule rule, Printer printer) {
        double progress = switch (rule.getTriggerType()) {
            case TIME_BASED -> {
                long days = ChronoUnit.DAYS.between(baselineDate(rule), LocalDate.now());
                yield days * 100.0 / rule.getTriggerValue();
            }
            case USAGE_HOURS -> (hours(printer) - rule.getLastUsageHoursSnapshot()) * 100.0 / rule.getTriggerValue();
            case FILAMENT_GRAMS -> (grams(printer) - rule.getLastFilamentSnapshot()) * 100.0 / rule.getTriggerValue();
        };
        return clamp(progress);
    }

    private LocalDate computeNextTrigger(MaintenanceRule rule) {
        if (rule.getTriggerType() != TriggerType.TIME_BASED) {
            return null;
        }
        return baselineDate(rule).plusDays(rule.getTriggerValue());
    }

    private Double computeCurrentValue(MaintenanceRule rule, Printer printer) {
        return switch (rule.getTriggerType()) {
            case USAGE_HOURS -> hours(printer) - rule.getLastUsageHoursSnapshot();
            case FILAMENT_GRAMS -> grams(printer) - rule.getLastFilamentSnapshot();
            case TIME_BASED -> null;
        };
    }

    // Línea base de tiempo: último disparo, o fecha de creación si nunca disparó.
    private LocalDate baselineDate(MaintenanceRule rule) {
        if (rule.getLastTriggeredAt() != null) {
            return rule.getLastTriggeredAt();
        }
        return rule.getCreatedAt() != null
                ? rule.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();
    }

    private double hours(Printer printer) {
        return printer.getTotalPrintingHours() != null ? printer.getTotalPrintingHours() : 0.0;
    }

    private double grams(Printer printer) {
        return printer.getTotalFilamentGrams() != null ? printer.getTotalFilamentGrams() : 0.0;
    }

    private double clamp(double value) {
        if (value < 0) return 0.0;
        if (value > 100) return 100.0;
        return Math.round(value * 10.0) / 10.0;
    }

    private String printerName(Printer printer) {
        if (printer == null) return null;
        if (printer.getName() != null && !printer.getName().isBlank()) return printer.getName();
        return printer.getModel();
    }

    // ── Serialización del checklist (JSON array de strings) ──────────────────
    private String serializeChecklist(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("No se pudo serializar el checklist.", e);
        }
    }

    private List<String> parseChecklist(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JacksonException e) {
            log.warn("Checklist inválido, se ignora: {}", json);
            return List.of();
        }
    }
}
