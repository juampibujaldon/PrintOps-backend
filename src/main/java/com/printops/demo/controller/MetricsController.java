// src/main/java/com/printops/demo/controller/MetricsController.java
package com.printops.demo.controller;

import com.printops.demo.dto.DashboardMetricsDTO;
import com.printops.demo.dto.MetricsFilters;
import com.printops.demo.service.GlobalMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);

    private final GlobalMetricsService globalMetricsService;

    public MetricsController(GlobalMetricsService globalMetricsService) {
        this.globalMetricsService = globalMetricsService;
    }

    // GET /api/metrics/dashboard?from=&to= (defaults: inicio del mes / hoy).
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardMetricsDTO> dashboard(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {

        LocalDate toDate = to != null ? to : LocalDate.now();
        LocalDate fromDate = from != null ? from : toDate.withDayOfMonth(1);

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("La fecha 'from' no puede ser posterior a 'to'.");
        }

        log.info("GET /api/metrics/dashboard from={} to={}", fromDate, toDate);
        return ResponseEntity.ok(globalMetricsService.getDashboardMetrics(new MetricsFilters(fromDate, toDate)));
    }
}
