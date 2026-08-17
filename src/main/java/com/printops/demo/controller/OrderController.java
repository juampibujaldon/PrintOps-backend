// src/main/java/com/printops/demo/controller/OrderController.java
package com.printops.demo.controller;

import com.printops.demo.dto.*;
import com.printops.demo.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // Crear orden (el checklist y las piezas viajan en el JSON del body).
    @PostMapping
    public ResponseEntity<OrderResponseDTO> create(@Valid @RequestBody CreateOrderRequest request) {
        log.info("POST /api/orders printerId={} type={}", request.printerId(), request.type());
        return ResponseEntity.ok(orderService.create(request));
    }

    // Listar con filtros opcionales.
    @GetMapping
    public ResponseEntity<List<OrderResponseDTO>> list(
            @RequestParam(required = false) Long printerId,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(orderService.list(printerId, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.get(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponseDTO> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String role = userDetails != null
                ? userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .findFirst().orElse("")
                : "";
        return ResponseEntity.ok(orderService.updateStatus(id, request, role));
    }

    @PostMapping("/{id}/parts")
    public ResponseEntity<OrderResponseDTO> addPart(
            @PathVariable Long id,
            @Valid @RequestBody AddPartRequest request) {
        return ResponseEntity.ok(orderService.addPart(id, request));
    }

    // Adjuntar fotos (hasta 5). Multipart con una o más partes "photos".
    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OrderResponseDTO> addPhotos(
            @PathVariable Long id,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        return ResponseEntity.ok(orderService.addPhotos(id, photos));
    }
}
