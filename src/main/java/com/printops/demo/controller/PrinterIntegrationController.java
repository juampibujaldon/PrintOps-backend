package com.printops.demo.controller;

import com.printops.demo.dto.IntegrationDTO;
import com.printops.demo.dto.PrinterTelemetryDTO;
import com.printops.demo.entity.IntegrationType;
import com.printops.demo.service.PrinterIntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/printers")
public class PrinterIntegrationController {

    private final PrinterIntegrationService integrationService;

    public PrinterIntegrationController(PrinterIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    // Estado en vivo de las impresoras integradas del taller.
    @GetMapping("/telemetry")
    public ResponseEntity<List<PrinterTelemetryDTO>> telemetry() {
        return ResponseEntity.ok(integrationService.telemetry());
    }

    @GetMapping("/{printerId}/integration")
    public ResponseEntity<IntegrationDTO> get(@PathVariable Long printerId) {
        return integrationService.get(printerId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    // Crea o rota el token del agente. Solo el supervisor.
    @PostMapping("/{printerId}/integration")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<IntegrationDTO> create(@PathVariable Long printerId,
                                                 @RequestBody(required = false) Map<String, String> body) {
        IntegrationType type = IntegrationType.OTHER;
        if (body != null && body.get("type") != null) {
            try {
                type = IntegrationType.valueOf(body.get("type").toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Tipo de integración inválido: " + body.get("type"));
            }
        }
        return ResponseEntity.ok(integrationService.createOrRotate(printerId, type));
    }

    @DeleteMapping("/{printerId}/integration")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long printerId) {
        integrationService.delete(printerId);
        return ResponseEntity.noContent().build();
    }
}
