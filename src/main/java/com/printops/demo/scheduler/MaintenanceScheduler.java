// src/main/java/com/printops/demo/scheduler/MaintenanceScheduler.java
package com.printops.demo.scheduler;

import com.printops.demo.service.MaintenanceRuleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Scheduler de mantenimiento preventivo (US-06). Evalúa las reglas activas
// cada hora para detectar condiciones cumplidas y generar órdenes automáticas.
@Component
public class MaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceScheduler.class);

    private final MaintenanceRuleService maintenanceRuleService;

    public MaintenanceScheduler(MaintenanceRuleService maintenanceRuleService) {
        this.maintenanceRuleService = maintenanceRuleService;
    }

    @Scheduled(fixedRate = 3_600_000) // cada hora (3.600.000 ms)
    // lockAtLeastFor: si otra réplica arranca su ciclo segundos después, no repite la evaluación.
    @SchedulerLock(name = "maintenance-rules", lockAtMostFor = "PT50M", lockAtLeastFor = "PT5M")
    public void evaluateRules() {
        log.info("Evaluando reglas de mantenimiento preventivo...");
        maintenanceRuleService.evaluateAllRules();
    }
}
