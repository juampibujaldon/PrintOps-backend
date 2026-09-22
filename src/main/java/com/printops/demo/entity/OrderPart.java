// src/main/java/com/printops/demo/entity/OrderPart.java
package com.printops.demo.entity;

import jakarta.persistence.*;

// Pieza usada en una orden (US-04 / US-10). Puede ser un repuesto del catálogo
// (sparePart) o una pieza externa (sparePart == null) con datos libres.
@Entity
@Table(name = "order_parts")
public class OrderPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private MaintenanceOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spare_part_id")
    private SparePart sparePart;

    @Column(nullable = false)
    private int quantity;

    // Datos de pieza externa (no en catálogo). Usados solo si sparePart == null.
    @Column
    private String externalPartName;

    @Column
    private String externalPartNumber;

    @Column
    private Double externalUnitPrice;

    public OrderPart() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MaintenanceOrder getOrder() { return order; }
    public void setOrder(MaintenanceOrder order) { this.order = order; }
    public SparePart getSparePart() { return sparePart; }
    public void setSparePart(SparePart sparePart) { this.sparePart = sparePart; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getExternalPartName() { return externalPartName; }
    public void setExternalPartName(String externalPartName) { this.externalPartName = externalPartName; }
    public String getExternalPartNumber() { return externalPartNumber; }
    public void setExternalPartNumber(String externalPartNumber) { this.externalPartNumber = externalPartNumber; }
    public Double getExternalUnitPrice() { return externalUnitPrice; }
    public void setExternalUnitPrice(Double externalUnitPrice) { this.externalUnitPrice = externalUnitPrice; }

    public boolean isExternal() { return sparePart == null; }
}
