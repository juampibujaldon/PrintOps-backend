// src/main/java/com/printops/demo/controller/QuoteController.java
package com.printops.demo.controller;

import com.printops.demo.dto.*;
import com.printops.demo.entity.QuoteStatus;
import com.printops.demo.service.QuoteService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private static final Logger log = LoggerFactory.getLogger(QuoteController.class);

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    // Cálculo en tiempo real SIN persistir (endpoint más usado; es puro cálculo).
    @PostMapping("/calculate")
    public ResponseEntity<QuoteResultsDTO> calculate(@Valid @RequestBody QuoteCalculateRequest request) {
        return ResponseEntity.ok(quoteService.calculate(request));
    }

    @PostMapping
    public ResponseEntity<QuoteResponseDTO> create(
            @Valid @RequestBody CreateQuoteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("POST /api/quotes user={}", userDetails.getUsername());
        return ResponseEntity.ok(quoteService.create(request, userDetails.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<QuoteResponseDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String clientName,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Long printerId) {
        QuoteStatus st = parseStatus(status);
        return ResponseEntity.ok(quoteService.list(st, clientName, from, to, printerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuoteResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(quoteService.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuoteResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateQuoteRequest request) {
        return ResponseEntity.ok(quoteService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<QuoteResponseDTO> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateQuoteStatusRequest request) {
        return ResponseEntity.ok(quoteService.changeStatus(id, QuoteStatus.valueOf(request.status())));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<QuoteResponseDTO> duplicate(@PathVariable Long id) {
        return ResponseEntity.ok(quoteService.duplicate(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        quoteService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Presupuesto eliminado"));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> getPdf(@PathVariable Long id) {
        QuoteResponseDTO quote = quoteService.get(id);
        byte[] pdf = quoteService.generateAndStorePdf(id);
        String filename = "presupuesto-" + quote.quoteNumber() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private QuoteStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return QuoteStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado inválido: " + status);
        }
    }
}
