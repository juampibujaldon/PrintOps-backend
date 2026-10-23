package com.printops.demo.controller;

import com.printops.demo.dto.ActivityItemDTO;
import com.printops.demo.dto.FailurePredictionDTO;
import com.printops.demo.dto.PageResponse;
import com.printops.demo.dto.ProfitabilityDTO;
import com.printops.demo.service.ActivityService;
import com.printops.demo.service.AnalyticsService;
import com.printops.demo.service.ExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

// Analítica, actividad y exportaciones (solo supervisor, ver SecurityConfig).
@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final ActivityService activityService;
    private final ExportService exportService;

    public AnalyticsController(AnalyticsService analyticsService, ActivityService activityService,
                               ExportService exportService) {
        this.analyticsService = analyticsService;
        this.activityService = activityService;
        this.exportService = exportService;
    }

    @GetMapping("/api/metrics/predictions")
    public ResponseEntity<List<FailurePredictionDTO>> predictions() {
        return ResponseEntity.ok(analyticsService.predictions());
    }

    @PostMapping("/api/metrics/predictions/{printerId}/preventive")
    public ResponseEntity<Map<String, Long>> schedule(@PathVariable Long printerId,
                                                      @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(Map.of("orderId", analyticsService.schedulePreventive(printerId, user.getUsername())));
    }

    @GetMapping("/api/metrics/profitability")
    public ResponseEntity<ProfitabilityDTO> profitability(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta.");
        return ResponseEntity.ok(analyticsService.profitability(start, end));
    }

    @GetMapping("/api/activity")
    public ResponseEntity<PageResponse<ActivityItemDTO>> activity(@RequestParam(required = false) String kind,
                                                                  @RequestParam(required = false) Integer page,
                                                                  @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(activityService.page(kind, page, size));
    }

    @GetMapping("/api/exports/{dataset}.csv")
    public ResponseEntity<byte[]> export(@PathVariable String dataset,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (!ExportService.DATASETS.contains(dataset)) {
            return ResponseEntity.notFound().build();
        }
        String name = "printops-" + dataset + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(exportService.export(dataset, from, to));
    }
}
