package com.printops.demo.service;

import com.printops.demo.dto.PurchaseOrderDTO;
import com.printops.demo.dto.PurchaseOrderRequest;
import com.printops.demo.entity.*;
import com.printops.demo.repository.PurchaseOrderRepository;
import com.printops.demo.repository.SparePartRepository;
import com.printops.demo.repository.UserRepository;
import com.printops.demo.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

// Órdenes de compra a proveedores. "Sugerir" arma borradores con los repuestos en stock
// bajo (agrupados por proveedor); al recibir se ingresa el stock de cada línea.
@Service
public class PurchaseOrderService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderService.class);
    private static final Set<PurchaseOrderStatus> OPEN = EnumSet.of(PurchaseOrderStatus.DRAFT, PurchaseOrderStatus.SENT);

    private final PurchaseOrderRepository orderRepository;
    private final SparePartRepository partRepository;
    private final UserRepository userRepository;
    private final SupplierService supplierService;
    private final SparePartService sparePartService;
    private final EmailService emailService;

    public PurchaseOrderService(PurchaseOrderRepository orderRepository, SparePartRepository partRepository,
                                UserRepository userRepository, SupplierService supplierService,
                                SparePartService sparePartService, EmailService emailService) {
        this.orderRepository = orderRepository;
        this.partRepository = partRepository;
        this.userRepository = userRepository;
        this.supplierService = supplierService;
        this.sparePartService = sparePartService;
        this.emailService = emailService;
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderDTO> list() {
        return orderRepository.findByWorkspaceIdOrderByCreatedAtDescIdDesc(wsId()).stream().map(PurchaseOrderService::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDTO get(Long id) {
        return toDTO(find(id));
    }

    @Transactional
    public PurchaseOrderDTO create(PurchaseOrderRequest r, String email) {
        PurchaseOrder po = new PurchaseOrder();
        po.setWorkspaceId(wsId());
        po.setStatus(PurchaseOrderStatus.DRAFT);
        po.setCreatedBy(userRepository.findByEmail(email).orElse(null));
        apply(po, r);
        return toDTO(saveWithNumber(po));
    }

    @Transactional
    public PurchaseOrderDTO update(Long id, PurchaseOrderRequest r) {
        PurchaseOrder po = find(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new IllegalArgumentException("Solo se puede editar una orden en borrador.");
        }
        po.getLines().clear();
        apply(po, r);
        return toDTO(orderRepository.save(po));
    }

    // Borradores con todo lo que está en stock bajo y no figura en otra orden abierta.
    @Transactional
    public List<PurchaseOrderDTO> suggest(String email) {
        Set<Long> alreadyOrdered = new HashSet<>(orderRepository.partIdsInOrders(wsId(), OPEN));
        Map<Optional<Supplier>, List<SparePart>> bySupplier = new LinkedHashMap<>();
        for (SparePart p : partRepository.findLowStock(wsId())) {
            if (alreadyOrdered.contains(p.getId())) continue;
            bySupplier.computeIfAbsent(Optional.ofNullable(p.getSupplier()), k -> new ArrayList<>()).add(p);
        }
        User user = userRepository.findByEmail(email).orElse(null);
        List<PurchaseOrderDTO> created = new ArrayList<>();
        bySupplier.forEach((supplier, parts) -> {
            PurchaseOrder po = new PurchaseOrder();
            po.setWorkspaceId(wsId());
            po.setStatus(PurchaseOrderStatus.DRAFT);
            po.setCreatedBy(user);
            po.setSupplier(supplier.orElse(null));
            po.setNotes("Generada automáticamente por stock bajo.");
            for (SparePart p : parts) {
                PurchaseOrderLine l = new PurchaseOrderLine();
                l.setSparePart(p);
                l.setQuantity(suggestedQuantity(p));
                l.setUnitCost(p.getUnitPrice());
                po.addLine(l);
            }
            created.add(toDTO(saveWithNumber(po)));
        });
        return created;
    }

    // Reponer hasta el doble del mínimo (al menos 1).
    static int suggestedQuantity(SparePart p) {
        int stock = p.getStock() != null ? p.getStock() : 0;
        int min = p.getMinStock() != null ? p.getMinStock() : 1;
        return Math.max(1, min * 2 - stock);
    }

    @Transactional
    public PurchaseOrderDTO send(Long id) {
        PurchaseOrder po = find(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new IllegalArgumentException("Solo se puede enviar una orden en borrador.");
        }
        po.setStatus(PurchaseOrderStatus.SENT);
        po.setSentAt(Instant.now());
        Supplier s = po.getSupplier();
        if (s != null && s.getEmail() != null) {
            try {
                emailService.send(s.getEmail(), "Orden de compra " + po.getNumber(), emailHtml(po));
            } catch (RuntimeException e) {
                // El pedido queda enviado igual: el mail es una cortesía, no bloquea la compra.
                log.warn("No se pudo mandar la orden {} a {}: {}", po.getNumber(), s.getEmail(), e.getMessage());
            }
        }
        return toDTO(orderRepository.save(po));
    }

    @Transactional
    public PurchaseOrderDTO receive(Long id, String email) {
        PurchaseOrder po = find(id);
        if (po.getStatus() != PurchaseOrderStatus.SENT && po.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new IllegalArgumentException("La orden ya fue recibida o cancelada.");
        }
        User user = userRepository.findByEmail(email).orElse(null);
        for (PurchaseOrderLine l : po.getLines()) {
            sparePartService.addPurchasedStock(l.getSparePart(), l.getQuantity(), l.getUnitCost(),
                    "Orden de compra " + po.getNumber(), user);
        }
        po.setStatus(PurchaseOrderStatus.RECEIVED);
        po.setReceivedAt(Instant.now());
        return toDTO(orderRepository.save(po));
    }

    @Transactional
    public PurchaseOrderDTO cancel(Long id) {
        PurchaseOrder po = find(id);
        if (!OPEN.contains(po.getStatus())) {
            throw new IllegalArgumentException("La orden ya fue recibida o cancelada.");
        }
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        return toDTO(orderRepository.save(po));
    }

    @Transactional
    public void delete(Long id) {
        PurchaseOrder po = find(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT && po.getStatus() != PurchaseOrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Solo se puede eliminar una orden en borrador o cancelada.");
        }
        orderRepository.delete(po);
    }

    private PurchaseOrder saveWithNumber(PurchaseOrder po) {
        PurchaseOrder saved = orderRepository.saveAndFlush(po);
        saved.setNumber(String.format("OC-%04d", saved.getId()));
        return orderRepository.save(saved);
    }

    private void apply(PurchaseOrder po, PurchaseOrderRequest r) {
        po.setSupplier(r.supplierId() != null ? supplierService.find(r.supplierId()) : null);
        po.setNotes(r.notes() == null || r.notes().isBlank() ? null : r.notes().trim());
        for (PurchaseOrderRequest.Line in : r.lines()) {
            SparePart part = partRepository.findByIdAndWorkspaceId(in.partId(), wsId())
                    .orElseThrow(() -> new NoSuchElementException("Repuesto no encontrado"));
            PurchaseOrderLine l = new PurchaseOrderLine();
            l.setSparePart(part);
            l.setQuantity(in.quantity());
            l.setUnitCost(in.unitCost() != null ? in.unitCost() : part.getUnitPrice());
            po.addLine(l);
        }
    }

    private PurchaseOrder find(Long id) {
        return orderRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Orden de compra no encontrada"));
    }

    private static String emailHtml(PurchaseOrder po) {
        StringBuilder rows = new StringBuilder();
        for (PurchaseOrderLine l : po.getLines()) {
            rows.append("<tr><td style=\"padding:6px 10px;border-bottom:1px solid #eee\">")
                    .append(escape(l.getSparePart().getName())).append(" (").append(escape(l.getSparePart().getPartNumber()))
                    .append(")</td><td style=\"padding:6px 10px;border-bottom:1px solid #eee;text-align:right\">")
                    .append(l.getQuantity()).append("</td></tr>");
        }
        return "<div style=\"font-family:Arial,sans-serif;color:#1c1c1e\"><h2>Orden de compra " + escape(po.getNumber())
                + "</h2><p>Hola, les pedimos los siguientes repuestos:</p>"
                + "<table style=\"border-collapse:collapse\"><tr><th style=\"text-align:left;padding:6px 10px\">Repuesto</th>"
                + "<th style=\"text-align:right;padding:6px 10px\">Cantidad</th></tr>" + rows + "</table>"
                + (po.getNotes() != null ? "<p>" + escape(po.getNotes()) + "</p>" : "")
                + "<p>Gracias.</p></div>";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static PurchaseOrderDTO toDTO(PurchaseOrder po) {
        Supplier s = po.getSupplier();
        List<PurchaseOrderDTO.Line> lines = po.getLines().stream().map(l -> {
            SparePart p = l.getSparePart();
            return new PurchaseOrderDTO.Line(l.getId(), p.getId(), p.getName(), p.getPartNumber(), l.getQuantity(),
                    l.getUnitCost(), p.getStock() != null ? p.getStock() : 0, p.getMinStock() != null ? p.getMinStock() : 0);
        }).toList();
        return new PurchaseOrderDTO(po.getId(), po.getNumber(), po.getStatus().name(),
                s != null ? s.getId() : null, s != null ? s.getName() : null, s != null ? s.getEmail() : null,
                po.getNotes(), Math.round(po.total() * 100.0) / 100.0, po.getCreatedAt(), po.getSentAt(),
                po.getReceivedAt(), lines);
    }
}
