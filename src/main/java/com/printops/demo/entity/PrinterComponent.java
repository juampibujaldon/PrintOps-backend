// src/main/java/com/printops/demo/entity/PrinterComponent.java
package com.printops.demo.entity;

import jakarta.persistence.*;

import java.time.Instant;

// Repuesto de desgaste instalado en una impresora: su calidad (vida útil) y desde
// qué horas de uso de la impresora está instalado. Si no hay fila, el componente
// se considera de calidad estándar instalado desde 0 h.
@Entity
@Table(name = "printer_components",
        uniqueConstraints = @UniqueConstraint(name = "uk_printer_component", columnNames = {"printer_id", "component"}))
public class PrinterComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "printer_id", nullable = false)
    private Long printerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComponentType component;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ComponentQuality quality = ComponentQuality.STANDARD;

    // Solo con quality = CUSTOM.
    private Double customLifespanHours;

    // Horas totales de la impresora al momento de instalar este repuesto.
    @Column(nullable = false)
    private Double installedAtHours = 0.0;

    private Instant replacedAt;

    // Evita repetir la alerta del 90% en cada presupuesto (se resetea al reemplazar).
    @Column(nullable = false)
    private boolean wearAlertSent = false;

    // Orden correctiva automática abierta por superar el 100% (evita duplicarla).
    private Long openOrderId;

    public Long getId() { return id; }
    public Long getPrinterId() { return printerId; }
    public void setPrinterId(Long printerId) { this.printerId = printerId; }
    public ComponentType getComponent() { return component; }
    public void setComponent(ComponentType component) { this.component = component; }
    public ComponentQuality getQuality() { return quality; }
    public void setQuality(ComponentQuality quality) { this.quality = quality; }
    public Double getCustomLifespanHours() { return customLifespanHours; }
    public void setCustomLifespanHours(Double customLifespanHours) { this.customLifespanHours = customLifespanHours; }
    public Double getInstalledAtHours() { return installedAtHours; }
    public void setInstalledAtHours(Double installedAtHours) { this.installedAtHours = installedAtHours; }
    public Instant getReplacedAt() { return replacedAt; }
    public void setReplacedAt(Instant replacedAt) { this.replacedAt = replacedAt; }
    public boolean isWearAlertSent() { return wearAlertSent; }
    public void setWearAlertSent(boolean wearAlertSent) { this.wearAlertSent = wearAlertSent; }
    public Long getOpenOrderId() { return openOrderId; }
    public void setOpenOrderId(Long openOrderId) { this.openOrderId = openOrderId; }
}
