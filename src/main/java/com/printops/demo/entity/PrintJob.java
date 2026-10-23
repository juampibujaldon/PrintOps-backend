package com.printops.demo.entity;

import jakarta.persistence.*;

import java.time.Instant;

// Trabajo de impresión: cierra el ciclo presupuesto aceptado → producción. Al terminar
// (DONE) se registra el uso real en la impresora y se descuenta la bobina.
@Entity
@Table(name = "print_jobs", indexes = {
        @Index(name = "idx_print_jobs_ws_status", columnList = "workspace_id, status, priority"),
        @Index(name = "idx_print_jobs_printer", columnList = "printer_id, status")})
public class PrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    // Presupuesto que originó el trabajo (uno por presupuesto).
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quote_id", unique = true)
    private Quote quote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "printer_id")
    private Printer printer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spool_id")
    private FilamentSpool spool;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PrintJobStatus status;

    // Mayor primero dentro de la cola.
    @Column(nullable = false)
    private Integer priority = 0;

    @Column(nullable = false)
    private Integer units = 1;

    // Totales del trabajo (todas las unidades).
    @Column(name = "estimated_hours", nullable = false)
    private Double estimatedHours = 0.0;

    @Column(name = "filament_grams", nullable = false)
    private Double filamentGrams = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "filament_type")
    private FilamentType filamentType;

    @Column(name = "actual_hours")
    private Double actualHours;

    @Column(name = "actual_grams")
    private Double actualGrams;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public Quote getQuote() { return quote; }
    public void setQuote(Quote quote) { this.quote = quote; }
    public Printer getPrinter() { return printer; }
    public void setPrinter(Printer printer) { this.printer = printer; }
    public FilamentSpool getSpool() { return spool; }
    public void setSpool(FilamentSpool spool) { this.spool = spool; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public PrintJobStatus getStatus() { return status; }
    public void setStatus(PrintJobStatus status) { this.status = status; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public Integer getUnits() { return units; }
    public void setUnits(Integer units) { this.units = units; }
    public Double getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(Double estimatedHours) { this.estimatedHours = estimatedHours; }
    public Double getFilamentGrams() { return filamentGrams; }
    public void setFilamentGrams(Double filamentGrams) { this.filamentGrams = filamentGrams; }
    public FilamentType getFilamentType() { return filamentType; }
    public void setFilamentType(FilamentType filamentType) { this.filamentType = filamentType; }
    public Double getActualHours() { return actualHours; }
    public void setActualHours(Double actualHours) { this.actualHours = actualHours; }
    public Double getActualGrams() { return actualGrams; }
    public void setActualGrams(Double actualGrams) { this.actualGrams = actualGrams; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
