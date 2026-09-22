// src/main/java/com/printops/demo/repository/MaintenanceRuleRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.MaintenanceRule;
import com.printops.demo.entity.TriggerType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MaintenanceRuleRepository extends JpaRepository<MaintenanceRule, Long> {

    // Todas las reglas activas (las que evalúa el scheduler).
    List<MaintenanceRule> findByActiveTrue();

    // Reglas activas de una impresora en particular.
    List<MaintenanceRule> findByPrinterIdAndActiveTrue(Long printerId);

    // Evita duplicar: solo una regla activa por triggerType e impresora.
    boolean existsByPrinterIdAndTriggerTypeAndActiveTrue(Long printerId, TriggerType type);
}
