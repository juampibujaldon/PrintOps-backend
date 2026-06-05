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

    @Column(unique = true, nullable = false)
    private String qrCodeData;

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
    public String getQrCodeData() { return qrCodeData; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }
}
