package com.printops.demo.entity;

import jakarta.persistence.*;

import java.time.Instant;

// Último estado reportado por el agente para una impresora (una fila por impresora).
@Entity
@Table(name = "printer_telemetry", indexes = @Index(name = "idx_printer_telemetry_ws", columnList = "workspace_id"))
public class PrinterTelemetry {

    // Estados normalizados (cada fuente se traduce en el agente).
    public static final java.util.Set<String> STATES =
            java.util.Set.of("IDLE", "PRINTING", "PAUSED", "FINISHED", "ERROR", "OFFLINE");

    @Id
    @Column(name = "printer_id")
    private Long printerId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false)
    private String state;

    private Double progress;

    @Column(name = "nozzle_temp")
    private Double nozzleTemp;

    @Column(name = "bed_temp")
    private Double bedTemp;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "remaining_seconds")
    private Integer remainingSeconds;

    private String message;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getPrinterId() { return printerId; }
    public void setPrinterId(Long printerId) { this.printerId = printerId; }
    public Long getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public Double getProgress() { return progress; }
    public void setProgress(Double progress) { this.progress = progress; }
    public Double getNozzleTemp() { return nozzleTemp; }
    public void setNozzleTemp(Double nozzleTemp) { this.nozzleTemp = nozzleTemp; }
    public Double getBedTemp() { return bedTemp; }
    public void setBedTemp(Double bedTemp) { this.bedTemp = bedTemp; }
    public String getJobName() { return jobName; }
    public void setJobName(String jobName) { this.jobName = jobName; }
    public Integer getRemainingSeconds() { return remainingSeconds; }
    public void setRemainingSeconds(Integer remainingSeconds) { this.remainingSeconds = remainingSeconds; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
