// src/main/java/com/printops/demo/service/SparePartService.java
package com.printops.demo.service;

import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.exception.InsufficientStockException;
import com.printops.demo.exception.StockConcurrencyException;
import com.printops.demo.repository.MaintenanceOrderRepository;
import com.printops.demo.repository.SparePartRepository;
import com.printops.demo.repository.StockMovementRepository;
import com.printops.demo.repository.UserRepository;
import com.printops.demo.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

// Control de stock de repuestos (US-10): CRUD, movimientos y descuento
// automático al completar una orden. Filtrado por workspace (FIX 3).
@Service
public class SparePartService {

    private static final Logger log = LoggerFactory.getLogger(SparePartService.class);

    private final SparePartRepository sparePartRepository;
    private final StockMovementRepository movementRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public SparePartService(SparePartRepository sparePartRepository,
                            StockMovementRepository movementRepository,
                            MaintenanceOrderRepository orderRepository,
                            NotificationService notificationService,
                            UserRepository userRepository) {
        this.sparePartRepository = sparePartRepository;
        this.movementRepository = movementRepository;
        this.orderRepository = orderRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(cacheNames = "dashboard-metrics", allEntries = true)
    public SparePartDTO createPart(CreateSparePartRequest req, User user) {
        if (sparePartRepository.existsByPartNumber(req.partNumber())) {
            throw new IllegalArgumentException("Ya existe una pieza con ese número de parte.");
        }
        SparePart part = new SparePart();
        apply(part, req);
        part.setWorkspaceId(wsId());
        part.setCreatedBy(user);
        return toDTO(sparePartRepository.save(part));
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard-metrics", allEntries = true)
    public SparePartDTO updatePart(Long id, CreateSparePartRequest req) {
        SparePart part = find(id);
        if (!part.getPartNumber().equals(req.partNumber())
                && sparePartRepository.existsByPartNumber(req.partNumber())) {
            throw new IllegalArgumentException("Ya existe una pieza con ese número de parte.");
        }
        apply(part, req);
        return toDTO(sparePartRepository.save(part));
    }

    @Transactional(readOnly = true)
    public List<SparePartDTO> list(String search, String category, boolean lowStock) {
        String s = search != null ? search.trim().toLowerCase() : "";
        return sparePartRepository.findByWorkspaceIdOrderByNameAsc(wsId()).stream()
                .filter(p -> s.isEmpty()
                        || (p.getName() != null && p.getName().toLowerCase().contains(s))
                        || (p.getPartNumber() != null && p.getPartNumber().toLowerCase().contains(s)))
                .filter(p -> category == null || category.isBlank() || category.equalsIgnoreCase(p.getCategory()))
                .filter(p -> !lowStock || p.getStock() <= p.getMinStock())
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public SparePartDTO get(Long id) {
        return toDTO(find(id));
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard-metrics", allEntries = true)
    public void delete(Long id) {
        SparePart part = find(id);
        if (movementRepository.existsBySparePartId(id)) {
            throw new IllegalArgumentException("No se puede eliminar: la pieza tiene movimientos de stock registrados.");
        }
        sparePartRepository.delete(part);
    }

    // ── Stock bajo y categorías ──────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<SparePartDTO> getLowStockParts() {
        return sparePartRepository.findLowStock(wsId()).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return sparePartRepository.findDistinctCategories(wsId());
    }

    // ── Movimientos ───────────────────────────────────────────────────────────
    @Transactional
    @CacheEvict(cacheNames = "dashboard-metrics", allEntries = true)
    public SparePartDTO updateStock(Long partId, StockUpdateRequest req, User user) {
        SparePart part = find(partId);
        int before = part.getStock();

        int after;
        int change;
        if (req.type() == MovementType.ADJUSTMENT) {
            after = req.quantity();
            change = after - before;
        } else {
            change = req.quantity();
            after = before + change;
        }

        if (after < 0) {
            throw new IllegalArgumentException("El stock no puede quedar negativo.");
        }

        part.setStock(after);
        SparePart saved = sparePartRepository.save(part);
        recordMovement(part, req.type(), before, change, after, blankToNull(req.note()), null, user);

        log.info("Stock de {} ajustado: {} → {} (tipo {})", part.getName(), before, after, req.type());
        return toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<StockMovementDTO> getMovements(Long partId) {
        find(partId); // valida que la pieza pertenezca al workspace
        return movementRepository.findBySparePartIdOrderByPerformedAtDesc(partId).stream()
                .map(m -> new StockMovementDTO(
                        m.getId(),
                        m.getType() != null ? m.getType().name() : null,
                        m.getQuantityBefore(),
                        m.getQuantityChange(),
                        m.getQuantityAfter(),
                        m.getNote(),
                        m.getPerformedBy() != null ? m.getPerformedBy().getEmail() : null,
                        m.getPerformedAt(),
                        m.getOrder() != null ? String.format("ORD-%05d", m.getOrder().getId()) : null))
                .toList();
    }

    // ── Descuento automático al completar una orden (US-10) ───────────────────
    @Transactional
    public void deductStockForOrder(Long orderId) {
        MaintenanceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + orderId));

        for (OrderPart op : order.getParts()) {
            SparePart part = op.getSparePart();
            if (part == null) {
                continue; // pieza externa: no hay stock que descontar
            }
            int qty = op.getQuantity();
            int before = part.getStock();
            if (before < qty) {
                throw new InsufficientStockException(
                        "Stock insuficiente para " + part.getName()
                                + " (disponible " + before + ", requerido " + qty + ")");
            }
            int after = before - qty;
            part.setStock(after);

            // FIX 4: saveAndFlush fuerza la verificación de @Version dentro del
            // método para poder traducir la colisión de concurrencia a 409.
            try {
                sparePartRepository.saveAndFlush(part);
            } catch (ObjectOptimisticLockingFailureException e) {
                throw new StockConcurrencyException("El stock fue modificado por otro proceso. Reintentá la operación.");
            }

            recordMovement(part, MovementType.ORDER_USE, before, -qty, after,
                    "Uso en orden #" + orderId, order, null);

            if (after <= part.getMinStock()) {
                notifyLowStock(part, after, order.getWorkspaceId());
                log.warn("Stock bajo tras orden {}: {} en {} unidades", orderId, part.getName(), after);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    // FIX 8: alerta de stock bajo dirigida a los supervisores del workspace.
    private void notifyLowStock(SparePart part, int after, Long workspaceId) {
        String message = "⚠️ Stock bajo: " + part.getName() + " tiene solo " + after
                + " unidades (mínimo: " + part.getMinStock() + ")";
        userRepository.findByRoleAndWorkspaceId(Role.MANAGER, workspaceId).forEach(manager ->
                notificationService.createFor(manager.getId(), "LOW_STOCK", message, null));
    }

    private SparePart find(Long id) {
        return sparePartRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Pieza no encontrada con id " + id));
    }

    private void apply(SparePart part, CreateSparePartRequest req) {
        part.setName(req.name());
        part.setPartNumber(req.partNumber());
        part.setDescription(blankToNull(req.description()));
        part.setBrand(blankToNull(req.brand()));
        part.setCategory(blankToNull(req.category()));
        part.setStock(req.stock());
        part.setMinStock(req.minStock());
        part.setUnitPrice(req.unitPrice());
        part.setSupplierUrl(blankToNull(req.supplierUrl()));
    }

    private void recordMovement(SparePart part, MovementType type, int before, int change, int after,
                                String note, MaintenanceOrder order, User user) {
        StockMovement m = new StockMovement();
        m.setSparePart(part);
        m.setType(type);
        m.setQuantityBefore(before);
        m.setQuantityChange(change);
        m.setQuantityAfter(after);
        m.setNote(note);
        m.setOrder(order);
        m.setPerformedBy(user);
        movementRepository.save(m);
    }

    private SparePartDTO toDTO(SparePart p) {
        int stock = p.getStock() != null ? p.getStock() : 0;
        int minStock = p.getMinStock() != null ? p.getMinStock() : 0;
        return new SparePartDTO(
                p.getId(),
                p.getName(),
                p.getPartNumber(),
                p.getDescription(),
                p.getBrand(),
                p.getCategory(),
                stock,
                minStock,
                p.getUnitPrice() != null ? p.getUnitPrice() : 0.0,
                p.getSupplierUrl(),
                stock <= minStock,
                stock == 0);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
