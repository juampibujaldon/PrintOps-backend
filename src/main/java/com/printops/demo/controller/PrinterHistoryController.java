// src/main/java/com/printops/demo/controller/PrinterHistoryController.java
package com.printops.demo.controller;

import com.printops.demo.dto.HistoryFilters;
import com.printops.demo.dto.PartUsageSummaryDTO;
import com.printops.demo.dto.PrinterHistoryDTO;
import com.printops.demo.dto.PrinterMetricsDTO;
import com.printops.demo.entity.OrderType;
import com.printops.demo.service.PrinterHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/printers")
public class PrinterHistoryController {

    private static final Logger log = LoggerFactory.getLogger(PrinterHistoryController.class);

    private final PrinterHistoryService historyService;

    public PrinterHistoryController(PrinterHistoryService historyService) {
        this.historyService = historyService;
    }

    // GET /api/printers/{id}/history?type=&from=&to=&page=&size=
    @GetMapping("/{id}/history")
    public ResponseEntity<PrinterHistoryDTO> history(
            @PathVariable Long id,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/printers/{}/history type={} from={} to={} page={}", id, type, from, to, page);
        HistoryFilters filters = new HistoryFilters(parseType(type), from, to, page, Math.max(1, size));
        return ResponseEntity.ok(historyService.getHistory(id, filters));
    }

    // GET /api/printers/{id}/history/metrics
    @GetMapping("/{id}/history/metrics")
    public ResponseEntity<PrinterMetricsDTO> metrics(@PathVariable Long id) {
        return ResponseEntity.ok(historyService.getMetrics(id));
    }

    // GET /api/printers/{id}/history/parts
    @GetMapping("/{id}/history/parts")
    public ResponseEntity<List<PartUsageSummaryDTO>> parts(@PathVariable Long id) {
        return ResponseEntity.ok(historyService.getPartsUsage(id));
    }

    private OrderType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return OrderType.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de orden inválido: " + type);
        }
    }
}
