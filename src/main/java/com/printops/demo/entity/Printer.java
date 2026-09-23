// src/main/java/com/printops/demo/entity/Printer.java
package com.printops.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "printers")
public class Printer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = true)
    private String name;

    @Column(nullable = false)
    private String model;

    @Column(unique = true, nullable = false)
    private String serialNumber;

    @Column(nullable = false)
    private LocalDate purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrinterStatus status;

    @Column(nullable = true)
    private String photoUrl;

    // Ubicación física de la impresora (FIX 3). Opcional.
    @Column(nullable = true)
    private String location;

    // Fecha del próximo mantenimiento programado (FIX 4). Opcional.
    @Column(nullable = true)
    private LocalDate nextMaintenanceDate;

    // Contadores acumulados de uso (US-06). Alimentan las reglas por horas/gramos.
    @Column(nullable = false, columnDefinition = "DOUBLE PRECISION DEFAULT 0")
    private Double totalPrintingHours = 0.0;

    @Column(nullable = false, columnDefinition = "DOUBLE PRECISION DEFAULT 0")
    private Double totalFilamentGrams = 0.0;

    // FIX 3: aislación multi-tenant. Cada impresora pertenece a un workspace.
    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(unique = true, nullable = false)
    private String qrCodeData;

    // Fallback defensivo: si el servicio no generó el QR, se asigna acá.
    @PrePersist
    protected void onCreate() {
        if (this.qrCodeData == null) {
            this.qrCodeData = UUID.randomUUID().toString();
        }
    }

    public Printer() {
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public PrinterStatus getStatus() { return status; }
    public void setStatus(PrinterStatus status) { this.status = status; }
    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDate getNextMaintenanceDate() { return nextMaintenanceDate; }
    public void setNextMaintenanceDate(LocalDate nextMaintenanceDate) { this.nextMaintenanceDate = nextMaintenanceDate; }
    public Double getTotalPrintingHours() { return totalPrintingHours; }
    public void setTotalPrintingHours(Double totalPrintingHours) { this.totalPrintingHours = totalPrintingHours; }
    public Double getTotalFilamentGrams() { return totalFilamentGrams; }
    public void setTotalFilamentGrams(Double totalFilamentGrams) { this.totalFilamentGrams = totalFilamentGrams; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public String getQrCodeData() { return qrCodeData; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }
}
