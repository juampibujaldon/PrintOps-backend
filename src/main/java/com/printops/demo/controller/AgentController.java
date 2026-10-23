package com.printops.demo.controller;

import com.printops.demo.dto.PrinterTelemetryDTO;
import com.printops.demo.dto.TelemetryReport;
import com.printops.demo.service.PrinterIntegrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Endpoint del agente local. Autenticación: "Authorization: Device <token>" (el token que
// se genera al vincular la impresora). Público para Spring Security; lo valida el servicio.
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final PrinterIntegrationService integrationService;

    public AgentController(PrinterIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @PostMapping("/telemetry")
    public ResponseEntity<PrinterTelemetryDTO> telemetry(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth,
                                                         @Valid @RequestBody TelemetryReport report) {
        String token = auth != null && auth.startsWith("Device ") ? auth.substring("Device ".length()).trim() : null;
        return ResponseEntity.ok(integrationService.ingest(token, report));
    }
}
