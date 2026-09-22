// src/main/java/com/printops/demo/entity/Part.java
package com.printops.demo.entity;

import jakarta.persistence.*;

import java.time.Instant;

// Pieza del catálogo de stock (US-04). Las órdenes referencian estas piezas.
@Entity
@Table(name = "parts")
public class Part {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String partNumber;

    @Column(nullable = false)
    private int stockQuantity;

    // Precio unitario de la pieza (US-07). Alimenta el cálculo de costos del historial.
    @Column
    private Double unitPrice;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (unitPrice == null) unitPrice = 0.0;
    }

    public Part() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }
    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; }
    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    public Instant getCreatedAt() { return createdAt; }
}
