// src/main/java/com/printops/demo/entity/Workspace.java
package com.printops.demo.entity;

import jakarta.persistence.*;

import java.time.Instant;

// Taller/workspace (1:1 con usuario). Lo crea el MANAGER al registrarse.
@Entity
@Table(name = "workspaces")
public class Workspace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ DEFAULT NOW()")
    private Instant createdAt;

    // ── Datos de contacto (pantalla Workspace/Taller) ──
    @Column
    private String phone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column
    private String address;

    @Column(name = "logo_url")
    private String logoUrl;

    // El default vive en Java: un DEFAULT en columnDefinition rompe el ALTER de ddl-auto=update.
    @Column(nullable = false, length = 3)
    private String currency = "ARS";

    // ── Valores por defecto del presupuesto ──
    @Column(name = "default_energy_price_kwh", columnDefinition = "DOUBLE PRECISION DEFAULT 0")
    private Double defaultEnergyPriceKwh = 0.0;

    @Column(name = "default_operator_hourly_rate", columnDefinition = "DOUBLE PRECISION DEFAULT 0")
    private Double defaultOperatorHourlyRate = 0.0;

    @Column(name = "default_design_hourly_rate", columnDefinition = "DOUBLE PRECISION DEFAULT 0")
    private Double defaultDesignHourlyRate = 0.0;

    @Column(name = "default_margin_percent", columnDefinition = "DOUBLE PRECISION DEFAULT 30")
    private Double defaultMarginPercent = 30.0;

    @Column(name = "quote_validity_days", columnDefinition = "INTEGER DEFAULT 15")
    private Integer quoteValidityDays = 15;

    // ── Layout Visual del Taller (JSON) ──
    @Column(name = "canvas_layout", columnDefinition = "TEXT")
    private String canvasLayout;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (currency == null) currency = "ARS";
        if (defaultEnergyPriceKwh == null) defaultEnergyPriceKwh = 0.0;
        if (defaultOperatorHourlyRate == null) defaultOperatorHourlyRate = 0.0;
        if (defaultDesignHourlyRate == null) defaultDesignHourlyRate = 0.0;
        if (defaultMarginPercent == null) defaultMarginPercent = 30.0;
        if (quoteValidityDays == null) quoteValidityDays = 15;
    }

    public Workspace() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Double getDefaultEnergyPriceKwh() { return defaultEnergyPriceKwh; }
    public void setDefaultEnergyPriceKwh(Double v) { this.defaultEnergyPriceKwh = v; }
    public Double getDefaultOperatorHourlyRate() { return defaultOperatorHourlyRate; }
    public void setDefaultOperatorHourlyRate(Double v) { this.defaultOperatorHourlyRate = v; }
    public Double getDefaultDesignHourlyRate() { return defaultDesignHourlyRate; }
    public void setDefaultDesignHourlyRate(Double v) { this.defaultDesignHourlyRate = v; }
    public Double getDefaultMarginPercent() { return defaultMarginPercent; }
    public void setDefaultMarginPercent(Double v) { this.defaultMarginPercent = v; }
    public Integer getQuoteValidityDays() { return quoteValidityDays; }
    public void setQuoteValidityDays(Integer v) { this.quoteValidityDays = v; }
    
    public String getCanvasLayout() { return canvasLayout; }
    public void setCanvasLayout(String canvasLayout) { this.canvasLayout = canvasLayout; }
}
