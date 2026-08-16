// src/main/java/com/printops/demo/entity/OrderPart.java
package com.printops.demo.entity;

import jakarta.persistence.*;

// Pieza usada en una orden (US-04). Puede venir del catálogo (part) o ser
// una "pieza externa" con número de parte libre.
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
    @JoinColumn(name = "part_id")
    private Part part;

    // Número de parte libre, usado cuando la pieza no está en el catálogo.
    @Column
    private String partNumber;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private boolean external;

    public OrderPart() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MaintenanceOrder getOrder() { return order; }
    public void setOrder(MaintenanceOrder order) { this.order = order; }
    public Part getPart() { return part; }
    public void setPart(Part part) { this.part = part; }
    public String getPartNumber() { return partNumber; }
    public void setPartNumber(String partNumber) { this.partNumber = partNumber; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public boolean isExternal() { return external; }
    public void setExternal(boolean external) { this.external = external; }
}
