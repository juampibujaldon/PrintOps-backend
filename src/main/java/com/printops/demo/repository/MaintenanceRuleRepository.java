// src/main/java/com/printops/demo/repository/MaintenanceRuleRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.MaintenanceRule;
import com.printops.demo.entity.TriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MaintenanceRuleRepository extends JpaRepository<MaintenanceRule, Long> {

    // Sin filtro de workspace: la usa el scheduler, que evalúa TODOS los tenants.
    // La impresora se trae en la misma consulta (el scheduler la lee en cada regla).
    @Query("SELECT r FROM MaintenanceRule r JOIN FETCH r.printer WHERE r.active = true")
    List<MaintenanceRule> findActiveWithPrinter();

    // Actualizaciones directas desde el scheduler (la regla viene desconectada de la
    // sesion): una sentencia UPDATE, sin el SELECT previo que haria un merge().
    @Modifying
    @Query("""
            UPDATE MaintenanceRule r SET r.lastTriggeredAt = :date, r.lastUsageHoursSnapshot = :hours,
                   r.lastFilamentSnapshot = :grams, r.alertSentAt = null
            WHERE r.id = :id
            """)
    void markTriggered(@Param("id") Long id, @Param("date") LocalDate date,
                       @Param("hours") Double hours, @Param("grams") Double grams);

    @Modifying
    @Query("UPDATE MaintenanceRule r SET r.alertSentAt = :date WHERE r.id = :id")
    void markAlertSent(@Param("id") Long id, @Param("date") LocalDate date);

    // Reglas activas de una impresora (la impresora ya fue validada como del tenant).
    List<MaintenanceRule> findByPrinterIdAndActiveTrue(Long printerId);

    boolean existsByPrinterIdAndTriggerTypeAndActiveTrue(Long printerId, TriggerType type);

    // FIX 3: consultas puntuales filtradas por workspace.
    Optional<MaintenanceRule> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
