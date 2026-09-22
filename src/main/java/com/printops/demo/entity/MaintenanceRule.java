// src/main/java/com/printops/demo/entity/MaintenanceRule.java
package com.printops.demo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.time.LocalDate;

// Regla de mantenimiento preventivo periódico (US-06). El scheduler evalúa
// estas reglas y genera órdenes automáticamente cuando se cumple la condición.
@Entity
@Table(name = "maintenance_rules")
public class MaintenanceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    @NotNull
    private Printer printer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @NotNull
    private TriggerType triggerType;

    // Umbral: días / horas / gramos según triggerType.
    @Column(nullable = false)
    @NotNull
    @Positive
    private Integer triggerValue;

    // Días de anticipación para la alerta previa. Por defecto 7.
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 7")
    private Integer alertDaysBefore = 7;

    @Enumerated(EnumType.STRING)
    @Column(name = "maintenance_type")
    private OrderType maintenanceType;

    // Plantilla del checklist serializada como JSON array de strings.
    @Column(columnDefinition = "TEXT")
    private String checklistTemplate;

    // Regla activa (se evalúa en el scheduler) o pausada.
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT true")
    private Boolean active = true;

    // Última vez que la regla disparó (generó una orden).
    @Column
    private LocalDate lastTriggeredAt;

    // Snapshot del contador de la impresora en el último disparo. Sirve para
    // calcular el progreso de USAGE_HOURS y FILAMENT_GRAMS (delta desde el reset).
    @Column(nullable = false, columnDefinition = "DOUBLE PRECISION DEFAULT 0")
    private Double lastUsageHoursSnapshot = 0.0;

    @Column(nullable = false, columnDefinition = "DOUBLE PRECISION DEFAULT 0")
    private Double lastFilamentSnapshot = 0.0;

    // Fecha en la que se envió la última alerta previa (evita spam horario).
    @Column
    private LocalDate alertSentAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (active == null) active = true;
        if (alertDaysBefore == null) alertDaysBefore = 7;
        if (lastUsageHoursSnapshot == null) lastUsageHoursSnapshot = 0.0;
        if (lastFilamentSnapshot == null) lastFilamentSnapshot = 0.0;
    }

    public MaintenanceRule() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Printer getPrinter() { return printer; }
    public void setPrinter(Printer printer) { this.printer = printer; }
    public TriggerType getTriggerType() { return triggerType; }
    public void setTriggerType(TriggerType triggerType) { this.triggerType = triggerType; }
    public Integer getTriggerValue() { return triggerValue; }
    public void setTriggerValue(Integer triggerValue) { this.triggerValue = triggerValue; }
    public Integer getAlertDaysBefore() { return alertDaysBefore; }
    public void setAlertDaysBefore(Integer alertDaysBefore) { this.alertDaysBefore = alertDaysBefore; }
    public OrderType getMaintenanceType() { return maintenanceType; }
    public void setMaintenanceType(OrderType maintenanceType) { this.maintenanceType = maintenanceType; }
    public String getChecklistTemplate() { return checklistTemplate; }
    public void setChecklistTemplate(String checklistTemplate) { this.checklistTemplate = checklistTemplate; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDate getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(LocalDate lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }
    public Double getLastUsageHoursSnapshot() { return lastUsageHoursSnapshot; }
    public void setLastUsageHoursSnapshot(Double lastUsageHoursSnapshot) { this.lastUsageHoursSnapshot = lastUsageHoursSnapshot; }
    public Double getLastFilamentSnapshot() { return lastFilamentSnapshot; }
    public void setLastFilamentSnapshot(Double lastFilamentSnapshot) { this.lastFilamentSnapshot = lastFilamentSnapshot; }
    public LocalDate getAlertSentAt() { return alertSentAt; }
    public void setAlertSentAt(LocalDate alertSentAt) { this.alertSentAt = alertSentAt; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
