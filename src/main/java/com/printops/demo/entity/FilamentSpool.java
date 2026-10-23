package com.printops.demo.entity;

import jakarta.persistence.*;

import java.time.Instant;

// Bobina de filamento: gramos restantes y costo real. Alimenta el precio por gramo de los
// presupuestos y se descuenta al terminar un trabajo de impresión.
@Entity
@Table(name = "filament_spools", indexes = @Index(name = "idx_spools_workspace", columnList = "workspace_id, archived"))
public class FilamentSpool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FilamentType material;

    private String color;
    private String brand;

    @Column(name = "initial_grams", nullable = false)
    private Double initialGrams;

    @Column(name = "remaining_grams", nullable = false)
    private Double remainingGrams;

    @Column(name = "cost_per_kg", nullable = false)
    private Double costPerKg;

    // Impresora en la que está cargada (opcional).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    private Printer printer;

    @Column(name = "low_alert_sent", nullable = false)
    private boolean lowAlertSent;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public double pricePerGram() {
        return costPerKg != null ? costPerKg / 1000.0 : 0.0;
    }

    public Long getId() { return id; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public FilamentType getMaterial() { return material; }
    public void setMaterial(FilamentType material) { this.material = material; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public Double getInitialGrams() { return initialGrams; }
    public void setInitialGrams(Double initialGrams) { this.initialGrams = initialGrams; }
    public Double getRemainingGrams() { return remainingGrams; }
    public void setRemainingGrams(Double remainingGrams) { this.remainingGrams = remainingGrams; }
    public Double getCostPerKg() { return costPerKg; }
    public void setCostPerKg(Double costPerKg) { this.costPerKg = costPerKg; }
    public Printer getPrinter() { return printer; }
    public void setPrinter(Printer printer) { this.printer = printer; }
    public boolean isLowAlertSent() { return lowAlertSent; }
    public void setLowAlertSent(boolean lowAlertSent) { this.lowAlertSent = lowAlertSent; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getCreatedAt() { return createdAt; }
}
