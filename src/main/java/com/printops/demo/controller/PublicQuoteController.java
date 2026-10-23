package com.printops.demo.controller;

import com.printops.demo.dto.PublicQuoteDTO;
import com.printops.demo.entity.Quote;
import com.printops.demo.entity.QuoteStatus;
import com.printops.demo.entity.Workspace;
import com.printops.demo.repository.WorkspaceRepository;
import com.printops.demo.service.QuoteService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

// Presupuesto compartido con el cliente (sin login). El token aleatorio del link es la
// única llave; el rate limit de /api/public/** frena el barrido de tokens.
@RestController
@RequestMapping("/api/public/quotes")
public class PublicQuoteController {

    private final QuoteService quoteService;
    private final WorkspaceRepository workspaceRepository;

    public PublicQuoteController(QuoteService quoteService, WorkspaceRepository workspaceRepository) {
        this.quoteService = quoteService;
        this.workspaceRepository = workspaceRepository;
    }

    @GetMapping("/{token}")
    public ResponseEntity<PublicQuoteDTO> get(@PathVariable String token) {
        Quote q = quoteService.findByPublicToken(token);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(toDTO(q));
    }

    @GetMapping("/{token}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String token) {
        Quote q = quoteService.findByPublicToken(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"presupuesto-" + q.getQuoteNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .body(quoteService.publicPdf(token));
    }

    @PostMapping("/{token}/accept")
    public ResponseEntity<PublicQuoteDTO> accept(@PathVariable String token, @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(toDTO(decide(token, true, body)));
    }

    @PostMapping("/{token}/reject")
    public ResponseEntity<PublicQuoteDTO> reject(@PathVariable String token, @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(toDTO(decide(token, false, body)));
    }

    private Quote decide(String token, boolean accept, Map<String, String> body) {
        Quote q = quoteService.findByPublicToken(token);
        if (isExpired(q)) {
            throw new IllegalArgumentException("El presupuesto venció. Pedí uno actualizado al taller.");
        }
        return quoteService.decideFromPublicLink(token, accept,
                body != null ? body.get("name") : null, body != null ? body.get("note") : null);
    }

    private boolean isExpired(Quote q) {
        LocalDate until = validUntil(q);
        return q.getStatus() == QuoteStatus.SENT && until != null && LocalDate.now().isAfter(until);
    }

    private LocalDate validUntil(Quote q) {
        Workspace w = q.getWorkspaceId() != null ? workspaceRepository.findById(q.getWorkspaceId()).orElse(null) : null;
        int days = w != null && w.getQuoteValidityDays() != null ? w.getQuoteValidityDays() : 15;
        java.time.Instant base = q.getSharedAt() != null ? q.getSharedAt() : null;
        LocalDate start = base != null ? base.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                : q.getCreatedAt() != null ? q.getCreatedAt().toLocalDate() : null;
        return start != null ? start.plusDays(days) : null;
    }

    private PublicQuoteDTO toDTO(Quote q) {
        Workspace w = q.getWorkspaceId() != null ? workspaceRepository.findById(q.getWorkspaceId()).orElse(null) : null;
        int units = q.getUnits() != null ? q.getUnits() : 1;
        double discount = q.getDiscountPercent() != null ? q.getDiscountPercent() : 0.0;
        double extras = q.getExtraCostsTotal() != null ? q.getExtraCostsTotal() : 0.0;
        double finalUnit = q.getTotalPrice() != null ? (q.getTotalPrice() - extras) / units : 0.0;
        PublicQuoteDTO.Workshop workshop = w == null ? null
                : new PublicQuoteDTO.Workshop(w.getName(), w.getContactEmail(), w.getPhone(), w.getAddress(), w.getLogoUrl());
        return new PublicQuoteDTO(q.getQuoteNumber(), q.getStatus().name(), q.getClientName(), q.getJobDescription(),
                units, Math.round(finalUnit * 100.0) / 100.0, discount, q.getTotalPrice() != null ? q.getTotalPrice() : 0.0,
                w != null ? w.getCurrency() : "ARS", validUntil(q), isExpired(q), q.getClientDecisionAt(), workshop,
                q.getExtraCosts().stream().map(e -> new com.printops.demo.dto.ExtraCostItem(e.getDescription(), e.getAmount())).toList());
    }
}
