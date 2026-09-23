// src/main/java/com/printops/demo/entity/Quote.java
package com.printops.demo.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Presupuesto de impresión 3D (US-11).
// Guarda tanto los parámetros de entrada como los resultados calculados,
// de modo que el historial sea estable aunque cambien los precios de referencia.
@Entity
@Table(name = "quotes")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Número secuencial legible ("PRE-0001", "PRE-0002", ...). Se asigna en el
    // servicio a partir del id autogenerado, para garantizar unicidad sin carreras.
    @Column(name = "quote_number", unique = true)
    private String quoteNumber;

    @Column(name = "client_name")
    private String clientName;

    @Column(name = "job_description")
    private String jobDescription;

    @Column(nullable = false)
    private Integer units = 1;

    // ── Materiales ──
    @Column(name = "filament_grams", nullable = false)
    private Double filamentGrams;

    @Column(name = "filament_price_per_gram", nullable = false)
    private Double filamentPricePerGram;

    @Enumerated(EnumType.STRING)
    @Column(name = "filament_type", nullable = false)
    private FilamentType filamentType;

    @Column(name = "printer_watts", nullable = false)
    private Double printerWatts;

    @Column(name = "energy_price_kwh", nullable = false)
    private Double energyPriceKwh;

    // ── Tiempo ──
    @Column(name = "printing_hours", nullable = false)
    private Double printingHours;

    @Column(name = "design_hours")
    private Double designHours;

    @Column(name = "design_hourly_rate")
    private Double designHourlyRate;

    @Column(name = "operator_hourly_rate")
    private Double operatorHourlyRate;

    // ── Negocio ──
    @Column(name = "margin_percent", nullable = false)
    private Double marginPercent = 30.0;

    @Column(name = "discount_percent")
    private Double discountPercent = 0.0;

    // ── Resultados calculados (persistidos para historial) ──
    @Column(name = "filament_cost") private Double filamentCost;
    @Column(name = "energy_cost") private Double energyCost;
    @Column(name = "design_cost") private Double designCost;
    @Column(name = "operator_cost") private Double operatorCost;
    @Column(name = "total_cost") private Double totalCost;
    @Column(name = "unit_price") private Double unitPrice;
    @Column(name = "total_price") private Double totalPrice;
    @Column(name = "total_profit") private Double totalProfit;
    @Column(name = "roi") private Double roi;

    // Desgaste de componentes persistido como JSON.
    @Column(name = "component_wear", columnDefinition = "TEXT")
    private String componentWear;

    // ── Relaciones ──
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    private Printer printer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // ── Estado y timestamps ──
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteStatus status = QuoteStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "pdf_path")
    private String pdfPath;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (units == null) units = 1;
        if (marginPercent == null) marginPercent = 30.0;
        if (discountPercent == null) discountPercent = 0.0;
        if (designHours == null) designHours = 0.0;
        if (designHourlyRate == null) designHourlyRate = 0.0;
        if (operatorHourlyRate == null) operatorHourlyRate = 0.0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Quote() {
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuoteNumber() { return quoteNumber; }
    public void setQuoteNumber(String quoteNumber) { this.quoteNumber = quoteNumber; }
    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }
    public String getJobDescription() { return jobDescription; }
    public void setJobDescription(String jobDescription) { this.jobDescription = jobDescription; }
    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }
    public Double getFilamentGrams() { return filamentGrams; }
    public void setFilamentGrams(Double filamentGrams) { this.filamentGrams = filamentGrams; }
    public Double getFilamentPricePerGram() { return filamentPricePerGram; }
    public void setFilamentPricePerGram(Double filamentPricePerGram) { this.filamentPricePerGram = filamentPricePerGram; }
    public FilamentType getFilamentType() { return filamentType; }
    public void setFilamentType(FilamentType filamentType) { this.filamentType = filamentType; }
    public Double getPrinterWatts() { return printerWatts; }
    public void setPrinterWatts(Double printerWatts) { this.printerWatts = printerWatts; }
    public Double getEnergyPriceKwh() { return energyPriceKwh; }
    public void setEnergyPriceKwh(Double energyPriceKwh) { this.energyPriceKwh = energyPriceKwh; }
    public Double getPrintingHours() { return printingHours; }
    public void setPrintingHours(Double printingHours) { this.printingHours = printingHours; }
    public Double getDesignHours() { return designHours; }
    public void setDesignHours(Double designHours) { this.designHours = designHours; }
    public Double getDesignHourlyRate() { return designHourlyRate; }
    public void setDesignHourlyRate(Double designHourlyRate) { this.designHourlyRate = designHourlyRate; }
    public Double getOperatorHourlyRate() { return operatorHourlyRate; }
    public void setOperatorHourlyRate(Double operatorHourlyRate) { this.operatorHourlyRate = operatorHourlyRate; }
    public Double getMarginPercent() { return marginPercent; }
    public void setMarginPercent(Double marginPercent) { this.marginPercent = marginPercent; }
    public Double getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Double discountPercent) { this.discountPercent = discountPercent; }
    public Double getFilamentCost() { return filamentCost; }
    public void setFilamentCost(Double filamentCost) { this.filamentCost = filamentCost; }
    public Double getEnergyCost() { return energyCost; }
    public void setEnergyCost(Double energyCost) { this.energyCost = energyCost; }
    public Double getDesignCost() { return designCost; }
    public void setDesignCost(Double designCost) { this.designCost = designCost; }
    public Double getOperatorCost() { return operatorCost; }
    public void setOperatorCost(Double operatorCost) { this.operatorCost = operatorCost; }
    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }
    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    public Double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(Double totalPrice) { this.totalPrice = totalPrice; }
    public Double getTotalProfit() { return totalProfit; }
    public void setTotalProfit(Double totalProfit) { this.totalProfit = totalProfit; }
    public Double getRoi() { return roi; }
    public void setRoi(Double roi) { this.roi = roi; }
    public String getComponentWear() { return componentWear; }
    public void setComponentWear(String componentWear) { this.componentWear = componentWear; }
    public Printer getPrinter() { return printer; }
    public void setPrinter(Printer printer) { this.printer = printer; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public QuoteStatus getStatus() { return status; }
    public void setStatus(QuoteStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getPdfPath() { return pdfPath; }
    public void setPdfPath(String pdfPath) { this.pdfPath = pdfPath; }
}
