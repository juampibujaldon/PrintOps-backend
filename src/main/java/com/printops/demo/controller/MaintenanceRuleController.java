// src/main/java/com/printops/demo/controller/MaintenanceRuleController.java
package com.printops.demo.controller;

import com.printops.demo.dto.CreateRuleRequest;
import com.printops.demo.dto.MaintenanceRuleDTO;
import com.printops.demo.entity.User;
import com.printops.demo.service.MaintenanceRuleService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class MaintenanceRuleController {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceRuleController.class);

    private final MaintenanceRuleService maintenanceRuleService;
    private final com.printops.demo.repository.UserRepository userRepository;

    public MaintenanceRuleController(MaintenanceRuleService maintenanceRuleService,
                                     com.printops.demo.repository.UserRepository userRepository) {
        this.maintenanceRuleService = maintenanceRuleService;
        this.userRepository = userRepository;
    }

    // Crear regla (POST /api/rules).
    @PostMapping
    public ResponseEntity<MaintenanceRuleDTO> create(
            @Valid @RequestBody CreateRuleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/rules printerId={} tipo={}", request.printerId(), request.triggerType());
        User user = resolveUser(userDetails);
        return ResponseEntity.ok(maintenanceRuleService.createRule(request, user));
    }

    // Reglas de una impresora con progreso (GET /api/rules?printerId=).
    @GetMapping
    public ResponseEntity<List<MaintenanceRuleDTO>> list(@RequestParam Long printerId) {
        return ResponseEntity.ok(maintenanceRuleService.getRulesForPrinter(printerId));
    }

    // Detalle (GET /api/rules/{id}).
    @GetMapping("/{id}")
    public ResponseEntity<MaintenanceRuleDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceRuleService.getRule(id));
    }

    // Pausar (PATCH /api/rules/{id}/pause).
    @PatchMapping("/{id}/pause")
    public ResponseEntity<MaintenanceRuleDTO> pause(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceRuleService.pauseRule(id));
    }

    // Reactivar (PATCH /api/rules/{id}/resume).
    @PatchMapping("/{id}/resume")
    public ResponseEntity<MaintenanceRuleDTO> resume(@PathVariable Long id) {
        return ResponseEntity.ok(maintenanceRuleService.resumeRule(id));
    }

    // Eliminar (DELETE /api/rules/{id}).
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        maintenanceRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    private User resolveUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalArgumentException("Usuario no autenticado.");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }
}
