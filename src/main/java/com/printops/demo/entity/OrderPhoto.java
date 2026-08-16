// src/main/java/com/printops/demo/entity/OrderPhoto.java
package com.printops.demo.entity;

import jakarta.persistence.*;

import java.time.Instant;

// Foto adjunta a una orden (US-04). Máximo 5 por orden.
@Entity
@Table(name = "order_photos")
public class OrderPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private MaintenanceOrder order;

    @Column(nullable = false)
    private String url;

    // Etiqueta opcional (ej. "antes", "después").
    @Column
    private String label;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    public OrderPhoto() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MaintenanceOrder getOrder() { return order; }
    public void setOrder(MaintenanceOrder order) { this.order = order; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Instant getCreatedAt() { return createdAt; }
}
