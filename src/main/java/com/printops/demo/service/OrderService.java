// src/main/java/com/printops/demo/service/OrderService.java
package com.printops.demo.service;

import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.repository.MaintenanceOrderRepository;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.repository.SparePartRepository;
import com.printops.demo.repository.StatusHistoryRepository;
import com.printops.demo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_PHOTOS_PER_ORDER = 5;

    private final MaintenanceOrderRepository orderRepository;
    private final PrinterRepository printerRepository;
    private final SparePartRepository sparePartRepository;
    private final UserRepository userRepository;
    private final StatusHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final SparePartService sparePartService;
    private final String uploadDir = "uploads/orders/";

    public OrderService(MaintenanceOrderRepository orderRepository,
                        PrinterRepository printerRepository,
                        SparePartRepository sparePartRepository,
                        UserRepository userRepository,
                        StatusHistoryRepository historyRepository,
                        NotificationService notificationService,
                        SparePartService sparePartService) {
        this.orderRepository = orderRepository;
        this.printerRepository = printerRepository;
        this.sparePartRepository = sparePartRepository;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
        this.sparePartService = sparePartService;
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads de órdenes.", e);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard-metrics", allEntries = true)
    public OrderResponseDTO create(CreateOrderRequest dto, String creatorEmail) {
        Printer printer = printerRepository.findById(dto.printerId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con id " + dto.printerId()));
        User creator = userRepository.findByEmail(creatorEmail)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));

        OrderType type = parseType(dto.type());

        if (printer.getStatus() == PrinterStatus.OUT_OF_SERVICE && type != OrderType.CORRECTIVE) {
            throw new IllegalArgumentException("La impresora está fuera de servicio: la orden debe ser Correctivo.");
        }
        if (type == OrderType.CORRECTIVE && (dto.description() == null || dto.description().isBlank())) {
            throw new IllegalArgumentException("La descripción del problema es obligatoria para órdenes correctivas.");
        }

        MaintenanceOrder order = new MaintenanceOrder();
        order.setPrinter(printer);
        order.setAssignedTo(creator); // técnico asignado = quien la crea (US-05)
        order.setType(type);
        order.setStatus(OrderStatus.PENDING);
        order.setDescription(blankToNull(dto.description()));
        order.setEstimatedTimeMinutes(dto.estimatedTimeMinutes());

        if (dto.checklistItems() != null) {
            for (CreateOrderRequest.ChecklistItemInput item : dto.checklistItems()) {
                OrderChecklistItem ci = new OrderChecklistItem();
                ci.setText(item.text());
                ci.setDone(item.done());
                ci.setNa(item.na());
                order.addChecklistItem(ci);
            }
        }

        // US-10: las piezas se registran en la orden pero el stock NO se descuenta
        // acá; se descuenta al completar la orden (deductStockForOrder).
        if (dto.parts() != null) {
            for (CreateOrderRequest.PartInput input : dto.parts()) {
                order.addPart(buildOrderPart(input));
            }
        }

        MaintenanceOrder saved = orderRepository.save(order);

        saveHistory(saved.getId(), null, OrderStatus.PENDING, null, creator);

        notificationService.createFor(
                creator.getId(),
                "ORDER_CREATED",
                "Nueva orden #" + saved.getId() + " creada para la impresora " + printer.getSerialNumber(),
                saved.getId());

        log.info("Orden creada id={} tipo={} impresora={} asignada a={}",
                saved.getId(), type, printer.getSerialNumber(), creator.getEmail());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> list(Long printerId, String status) {
        List<MaintenanceOrder> orders;
        if (printerId != null && status != null) {
            orders = orderRepository.findByPrinterIdAndStatus(printerId, parseStatus(status));
        } else if (printerId != null) {
            orders = orderRepository.findByPrinterId(printerId);
        } else if (status != null) {
            orders = orderRepository.findByStatus(parseStatus(status));
        } else {
            orders = orderRepository.findAll();
        }
        return orders.stream()
                .sorted(Comparator
                        .comparing((MaintenanceOrder o) -> o.getStatus() != OrderStatus.IN_REVIEW)
                        .thenComparing(MaintenanceOrder::getCreatedAt, Comparator.reverseOrder()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO get(Long id) {
        return toResponse(orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id)));
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard-metrics", allEntries = true)
    public OrderResponseDTO changeStatus(Long id, StatusChangeRequest request, String role, String email) {
        MaintenanceOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id));
        User actor = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));

        OrderStatus from = order.getStatus();
        OrderStatus to = parseStatus(request.newStatus());

        boolean isManager = role != null && role.contains("MANAGER");

        validateTransition(order, from, to, isManager, actor);

        if (from == OrderStatus.IN_REVIEW && to == OrderStatus.IN_PROGRESS
                && (request.comment() == null || request.comment().isBlank())) {
            throw new IllegalArgumentException("El comentario es obligatorio para rechazar una orden.");
        }

        // US-10: al completar la orden se descuenta el stock de las piezas usadas.
        // Si falta stock, se lanza InsufficientStockException (400) y la transición
        // no se persiste (rollback de la transacción).
        if (to == OrderStatus.COMPLETED) {
            sparePartService.deductStockForOrder(order.getId());
        }

        order.setStatus(to);
        OrderResponseDTO response = toResponse(orderRepository.save(order));

        saveHistory(order.getId(), from, to, blankToNull(request.comment()), actor);

        notifyStatusChange(order, from, to, actor, request.comment());

        return response;
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getHistory(Long id) {
        return historyRepository.findByOrderIdOrderByChangedAtDesc(id).stream()
                .map(h -> new StatusHistoryResponse(
                        h.getId(),
                        h.getOrderId(),
                        h.getFromStatus() != null ? h.getFromStatus().name() : null,
                        h.getToStatus() != null ? h.getToStatus().name() : null,
                        h.getComment(),
                        h.getChangedBy() != null ? h.getChangedBy().getId() : null,
                        h.getChangedBy() != null ? h.getChangedBy().getEmail() : null,
                        h.getChangedAt()))
                .toList();
    }

    private void validateTransition(MaintenanceOrder order, OrderStatus from, OrderStatus to,
                                    boolean isManager, User actor) {
        boolean isAssigned = order.getAssignedTo() != null
                && order.getAssignedTo().getId().equals(actor.getId());

        boolean allowed;
        if (isManager) {
            allowed = switch (to) {
                case COMPLETED -> from == OrderStatus.IN_REVIEW;
                case IN_PROGRESS -> from == OrderStatus.IN_REVIEW; // rechazo
                case CANCELLED -> from == OrderStatus.PENDING || from == OrderStatus.IN_PROGRESS || from == OrderStatus.IN_REVIEW;
                default -> false;
            };
        } else {
            if (!isAssigned) {
                throw new IllegalArgumentException("Solo el técnico asignado puede mover esta orden.");
            }
            allowed = switch (to) {
                case IN_PROGRESS -> from == OrderStatus.PENDING;
                case IN_REVIEW -> from == OrderStatus.IN_PROGRESS;
                case CANCELLED -> from == OrderStatus.PENDING || from == OrderStatus.IN_PROGRESS;
                default -> false;
            };
        }

        if (!allowed) {
            throw new IllegalArgumentException("Transición no permitida de " + from + " a " + to + " para tu rol.");
        }
    }

    private void notifyStatusChange(MaintenanceOrder order, OrderStatus from, OrderStatus to,
                                    User actor, String comment) {
        Long assignedId = order.getAssignedTo() != null ? order.getAssignedTo().getId() : null;
        Long orderId = order.getId();
        String actorName = actor.getEmail();

        if (to == OrderStatus.IN_PROGRESS && from == OrderStatus.PENDING) {
            notificationService.createFor(null, "ORDER_STARTED",
                    "Orden #" + orderId + " iniciada por " + actorName, orderId);
        } else if (to == OrderStatus.IN_REVIEW) {
            notificationService.createFor(null, "ORDER_REVIEW",
                    "Orden #" + orderId + " lista para revisión ✓", orderId);
        } else if (to == OrderStatus.COMPLETED) {
            if (assignedId != null) {
                notificationService.createFor(assignedId, "ORDER_COMPLETED",
                        "Orden #" + orderId + " aprobada y cerrada ✓", orderId);
            }
        } else if (to == OrderStatus.IN_PROGRESS && from == OrderStatus.IN_REVIEW) {
            if (assignedId != null) {
                notificationService.createFor(assignedId, "ORDER_REJECTED",
                        "Orden #" + orderId + " rechazada — ver comentario", orderId);
            }
        }
    }

    private void saveHistory(Long orderId, OrderStatus from, OrderStatus to, String comment, User actor) {
        StatusHistory h = new StatusHistory();
        h.setOrderId(orderId);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setComment(comment);
        h.setChangedBy(actor);
        historyRepository.save(h);
    }

    @Transactional
    public OrderResponseDTO addPart(Long id, AddPartRequest request) {
        MaintenanceOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id));

        order.addPart(buildOrderPart(new CreateOrderRequest.PartInput(
                request.partId(), request.quantity(),
                request.externalPartName(), request.externalPartNumber(), request.externalUnitPrice())));
        return toResponse(orderRepository.save(order));
    }

    // US-10: quitar una pieza de una orden (solo antes de completarse).
    @Transactional
    public OrderResponseDTO removePart(Long orderId, Long partId) {
        MaintenanceOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + orderId));

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("No se pueden modificar piezas de una orden cerrada.");
        }

        order.getParts().removeIf(op -> op.getId().equals(partId));
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponseDTO addPhotos(Long id, List<MultipartFile> photos) {
        MaintenanceOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id));

        if (photos == null || photos.isEmpty()) {
            return toResponse(order);
        }

        if (order.getPhotos().size() + photos.size() > MAX_PHOTOS_PER_ORDER) {
            throw new IllegalArgumentException("No se pueden adjuntar más de " + MAX_PHOTOS_PER_ORDER + " fotos por orden.");
        }

        for (MultipartFile photo : photos) {
            if (photo == null || photo.isEmpty()) {
                continue;
            }
            String fileName = UUID.randomUUID() + "_" + photo.getOriginalFilename();
            try {
                Path targetLocation = Paths.get(uploadDir).resolve(fileName);
                Files.copy(photo.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
                String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/uploads/orders/")
                        .path(fileName)
                        .toUriString();
                OrderPhoto op = new OrderPhoto();
                op.setUrl(url);
                order.addPhoto(op);
            } catch (IOException e) {
                log.error("Error guardando foto de la orden {}", id, e);
                throw new RuntimeException("No se pudo guardar la foto.", e);
            }
        }
        return toResponse(orderRepository.save(order));
    }

    private OrderPart buildOrderPart(CreateOrderRequest.PartInput input) {
        OrderPart op = new OrderPart();
        op.setQuantity(input.quantity() != null ? input.quantity() : 1);

        if (input.partId() != null) {
            SparePart part = sparePartRepository.findById(input.partId())
                    .orElseThrow(() -> new NoSuchElementException("Pieza no encontrada con id " + input.partId()));
            op.setSparePart(part);
        } else {
            op.setExternalPartName(blankToNull(input.externalPartName()));
            op.setExternalPartNumber(blankToNull(input.externalPartNumber()));
            op.setExternalUnitPrice(input.externalUnitPrice());
        }
        return op;
    }

    private OrderType parseType(String type) {
        try {
            return OrderType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Tipo de orden inválido: " + type);
        }
    }

    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Estado de orden inválido: " + status);
        }
    }

    private OrderResponseDTO toResponse(MaintenanceOrder o) {
        List<OrderResponseDTO.ChecklistItemResponse> checklist = o.getChecklistItems().stream()
                .map(ci -> new OrderResponseDTO.ChecklistItemResponse(ci.getId(), ci.getText(), ci.isDone(), ci.isNa()))
                .toList();

        List<OrderResponseDTO.PartResponse> parts = o.getParts().stream()
                .map(p -> {
                    SparePart sp = p.getSparePart();
                    boolean external = sp == null;
                    return new OrderResponseDTO.PartResponse(
                            p.getId(),
                            sp != null ? sp.getId() : null,
                            sp != null ? sp.getName() : p.getExternalPartName(),
                            sp != null ? sp.getPartNumber() : p.getExternalPartNumber(),
                            p.getQuantity(),
                            external,
                            p.getExternalPartName(),
                            p.getExternalPartNumber(),
                            p.getExternalUnitPrice());
                })
                .toList();

        List<OrderResponseDTO.PhotoResponse> photos = o.getPhotos().stream()
                .map(ph -> new OrderResponseDTO.PhotoResponse(ph.getId(), ph.getUrl(), ph.getLabel()))
                .toList();

        return new OrderResponseDTO(
                o.getId(),
                o.getPrinter() != null ? o.getPrinter().getId() : null,
                o.getAssignedTo() != null ? o.getAssignedTo().getId() : null,
                o.getAssignedTo() != null ? o.getAssignedTo().getEmail() : null,
                o.getType() != null ? o.getType().name() : null,
                o.getStatus() != null ? o.getStatus().name() : null,
                o.getDescription(),
                o.getEstimatedTimeMinutes(),
                o.getActualTimeMinutes(),
                o.getCreatedAt(),
                checklist,
                parts,
                photos
        );
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
