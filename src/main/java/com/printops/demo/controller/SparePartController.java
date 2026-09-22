// src/main/java/com/printops/demo/controller/SparePartController.java
package com.printops.demo.controller;

import com.printops.demo.dto.*;
import com.printops.demo.entity.User;
import com.printops.demo.repository.UserRepository;
import com.printops.demo.service.SparePartService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/parts")
public class SparePartController {

    private static final Logger log = LoggerFactory.getLogger(SparePartController.class);

    private final SparePartService sparePartService;
    private final UserRepository userRepository;

    public SparePartController(SparePartService sparePartService, UserRepository userRepository) {
        this.sparePartService = sparePartService;
        this.userRepository = userRepository;
    }

    // GET /api/parts/categories (antes de {id} para evitar colisión de ruta)
    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(sparePartService.getCategories());
    }

    // GET /api/parts/low-stock
    @GetMapping("/low-stock")
    public ResponseEntity<List<SparePartDTO>> lowStock() {
        return ResponseEntity.ok(sparePartService.getLowStockParts());
    }

    @PostMapping
    public ResponseEntity<SparePartDTO> create(
            @Valid @RequestBody CreateSparePartRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(sparePartService.createPart(request, resolveUser(userDetails)));
    }

    @GetMapping
    public ResponseEntity<List<SparePartDTO>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(sparePartService.list(search, category, lowStock));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SparePartDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(sparePartService.get(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SparePartDTO> update(@PathVariable Long id,
                                               @Valid @RequestBody CreateSparePartRequest request) {
        return ResponseEntity.ok(sparePartService.updatePart(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        sparePartService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Pieza eliminada"));
    }

    // PATCH /api/parts/{id}/stock → ajustar stock manualmente.
    @PatchMapping("/{id}/stock")
    public ResponseEntity<SparePartDTO> updateStock(
            @PathVariable Long id,
            @Valid @RequestBody StockUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(sparePartService.updateStock(id, request, resolveUser(userDetails)));
    }

    // GET /api/parts/{id}/movements → historial de movimientos.
    @GetMapping("/{id}/movements")
    public ResponseEntity<List<StockMovementDTO>> movements(@PathVariable Long id) {
        return ResponseEntity.ok(sparePartService.getMovements(id));
    }

    private User resolveUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalArgumentException("Usuario no autenticado.");
        }
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
    }
}
