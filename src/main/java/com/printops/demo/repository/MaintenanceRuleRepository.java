// src/main/java/com/printops/demo/repository/MaintenanceRuleRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.MaintenanceRule;
import com.printops.demo.entity.TriggerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaintenanceRuleRepository extends JpaRepository<MaintenanceRule, Long> {

    // Sin filtro de workspace: la usa el scheduler, que evalúa TODOS los tenants.
    List<MaintenanceRule> findByActiveTrue();

    // Reglas activas de una impresora (la impresora ya fue validada como del tenant).
    List<MaintenanceRule> findByPrinterIdAndActiveTrue(Long printerId);

    boolean existsByPrinterIdAndTriggerTypeAndActiveTrue(Long printerId, TriggerType type);

    // FIX 3: consultas puntuales filtradas por workspace.
    Optional<MaintenanceRule> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
