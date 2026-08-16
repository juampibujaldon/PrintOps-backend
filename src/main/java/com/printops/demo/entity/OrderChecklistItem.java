// src/main/java/com/printops/demo/entity/OrderChecklistItem.java
package com.printops.demo.entity;

import jakarta.persistence.*;

// Ítem de checklist de una orden (US-04). Es editable: se puede tildar,
// marcar como N/A o agregar ítems nuevos.
@Entity
@Table(name = "order_checklist_items")
public class OrderChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private MaintenanceOrder order;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private boolean done;

    @Column(nullable = false)
    private boolean na;

    public OrderChecklistItem() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public MaintenanceOrder getOrder() { return order; }
    public void setOrder(MaintenanceOrder order) { this.order = order; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
    public boolean isNa() { return na; }
    public void setNa(boolean na) { this.na = na; }
}
