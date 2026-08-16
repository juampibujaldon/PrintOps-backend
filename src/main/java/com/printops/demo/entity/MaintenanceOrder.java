// src/main/java/com/printops/demo/entity/MaintenanceOrder.java
package com.printops.demo.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Orden de trabajo de mantenimiento (US-04). Corazón de la trazabilidad.
@Entity
@Table(name = "maintenance_orders")
public class MaintenanceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    private Printer printer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // Descripción del problema / trabajo. Obligatoria para órdenes correctivas.
    @Column
    private String description;

    // Tiempo estimado (minutos), ingresado al crear.
    @Column
    private Integer estimatedTimeMinutes;

    // Tiempo real (minutos), ingresado por el técnico al cerrar.
    @Column
    private Integer actualTimeMinutes;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderChecklistItem> checklistItems = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderPart> parts = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderPhoto> photos = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) status = OrderStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public MaintenanceOrder() {
    }

    // Helpers para mantener la relación bidireccional.
    public void addChecklistItem(OrderChecklistItem item) {
        checklistItems.add(item);
        item.setOrder(this);
    }

    public void addPart(OrderPart part) {
        parts.add(part);
        part.setOrder(this);
    }

    public void addPhoto(OrderPhoto photo) {
        photos.add(photo);
        photo.setOrder(this);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Printer getPrinter() { return printer; }
    public void setPrinter(Printer printer) { this.printer = printer; }
    public OrderType getType() { return type; }
    public void setType(OrderType type) { this.type = type; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getEstimatedTimeMinutes() { return estimatedTimeMinutes; }
    public void setEstimatedTimeMinutes(Integer estimatedTimeMinutes) { this.estimatedTimeMinutes = estimatedTimeMinutes; }
    public Integer getActualTimeMinutes() { return actualTimeMinutes; }
    public void setActualTimeMinutes(Integer actualTimeMinutes) { this.actualTimeMinutes = actualTimeMinutes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderChecklistItem> getChecklistItems() { return checklistItems; }
    public List<OrderPart> getParts() { return parts; }
    public List<OrderPhoto> getPhotos() { return photos; }
}
