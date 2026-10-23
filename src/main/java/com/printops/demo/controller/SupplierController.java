package com.printops.demo.controller;

import com.printops.demo.dto.PurchaseOrderDTO;
import com.printops.demo.dto.PurchaseOrderRequest;
import com.printops.demo.dto.SparePartDTO;
import com.printops.demo.dto.SupplierDTO;
import com.printops.demo.service.PurchaseOrderService;
import com.printops.demo.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Proveedores y órdenes de compra (solo supervisor, ver SecurityConfig).
@RestController
public class SupplierController {

    private final SupplierService supplierService;
    private final PurchaseOrderService orderService;

    public SupplierController(SupplierService supplierService, PurchaseOrderService orderService) {
        this.supplierService = supplierService;
        this.orderService = orderService;
    }

    @GetMapping("/api/suppliers")
    public ResponseEntity<List<SupplierDTO>> list() {
        return ResponseEntity.ok(supplierService.list());
    }

    @PostMapping("/api/suppliers")
    public ResponseEntity<SupplierDTO> create(@Valid @RequestBody SupplierDTO body) {
        return ResponseEntity.ok(supplierService.create(body));
    }

    @PutMapping("/api/suppliers/{id}")
    public ResponseEntity<SupplierDTO> update(@PathVariable Long id, @Valid @RequestBody SupplierDTO body) {
        return ResponseEntity.ok(supplierService.update(id, body));
    }

    @DeleteMapping("/api/suppliers/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // Proveedor habitual de un repuesto (null lo desvincula).
    @PatchMapping("/api/parts/{partId}/supplier")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<SparePartDTO> assign(@PathVariable Long partId, @RequestBody Map<String, Long> body) {
        return ResponseEntity.ok(supplierService.assignToPart(partId, body.get("supplierId")));
    }

    @GetMapping("/api/purchase-orders")
    public ResponseEntity<List<PurchaseOrderDTO>> orders() {
        return ResponseEntity.ok(orderService.list());
    }

    @GetMapping("/api/purchase-orders/{id}")
    public ResponseEntity<PurchaseOrderDTO> order(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.get(id));
    }

    @PostMapping("/api/purchase-orders")
    public ResponseEntity<PurchaseOrderDTO> createOrder(@Valid @RequestBody PurchaseOrderRequest body,
                                                        @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(orderService.create(body, user.getUsername()));
    }

    @PutMapping("/api/purchase-orders/{id}")
    public ResponseEntity<PurchaseOrderDTO> updateOrder(@PathVariable Long id, @Valid @RequestBody PurchaseOrderRequest body) {
        return ResponseEntity.ok(orderService.update(id, body));
    }

    @PostMapping("/api/purchase-orders/suggest")
    public ResponseEntity<List<PurchaseOrderDTO>> suggest(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(orderService.suggest(user.getUsername()));
    }

    @PostMapping("/api/purchase-orders/{id}/send")
    public ResponseEntity<PurchaseOrderDTO> send(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.send(id));
    }

    @PostMapping("/api/purchase-orders/{id}/receive")
    public ResponseEntity<PurchaseOrderDTO> receive(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(orderService.receive(id, user.getUsername()));
    }

    @PostMapping("/api/purchase-orders/{id}/cancel")
    public ResponseEntity<PurchaseOrderDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.cancel(id));
    }

    @DeleteMapping("/api/purchase-orders/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
