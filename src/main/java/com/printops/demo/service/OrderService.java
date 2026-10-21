// src/main/java/com/printops/demo/service/OrderService.java
package com.printops.demo.service;

import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.repository.MaintenanceOrderRepository;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.repository.SparePartRepository;
import com.printops.demo.repository.StatusHistoryRepository;
import com.printops.demo.repository.UserRepository;
import com.printops.demo.security.TenantContext;
import com.printops.demo.util.UploadFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.NoSuchElementException;

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
    private final StatusTransitionValidator statusTransitionValidator;
    private final SimpMessagingTemplate messagingTemplate;
    private final DashboardCache dashboardCache;
    private final String uploadDir = "uploads/orders/";

    public OrderService(MaintenanceOrderRepository orderRepository,
                        PrinterRepository printerRepository,
                        SparePartRepository sparePartRepository,
                        UserRepository userRepository,
                        StatusHistoryRepository historyRepository,
                        NotificationService notificationService,
                        SparePartService sparePartService,
                        StatusTransitionValidator statusTransitionValidator,
                        SimpMessagingTemplate messagingTemplate,
                        DashboardCache dashboardCache) {
        this.orderRepository = orderRepository;
        this.printerRepository = printerRepository;
        this.sparePartRepository = sparePartRepository;
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.notificationService = notificationService;
        this.sparePartService = sparePartService;
        this.statusTransitionValidator = statusTransitionValidator;
        this.messagingTemplate = messagingTemplate;
        this.dashboardCache = dashboardCache;
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads de órdenes.", e);
        }
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    @Transactional
    public OrderResponseDTO create(CreateOrderRequest dto, String creatorEmail) {
        Printer printer = printerRepository.findByIdAndWorkspaceId(dto.printerId(), wsId())
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
        order.setWorkspaceId(wsId());
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

        dashboardCache.invalidate();
        log.info("Orden creada id={} tipo={} impresora={} asignada a={}",
                saved.getId(), type, printer.getSerialNumber(), creator.getEmail());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> list(Long printerId, String status) {
        // El orden (IN_REVIEW primero, luego mas recientes) lo resuelve la consulta.
        OrderStatus filter = status != null ? parseStatus(status) : null;
        return orderRepository.findForList(wsId(), printerId, filter).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO get(Long id) {
        return toResponse(orderRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id)));
    }

    @Transactional
    public OrderResponseDTO changeStatus(Long id, StatusChangeRequest request, String email) {
        MaintenanceOrder order = orderRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id));
        User actor = userRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));

        OrderStatus from = order.getStatus();
        OrderStatus to = parseStatus(request.newStatus());

        // Las órdenes generadas automáticamente (reglas) nacen sin técnico: el
        // primer técnico que la inicia la toma. Sin esto nadie podía moverlas.
        if (order.getAssignedTo() == null && actor.getRole() == Role.TECNICO
                && from == OrderStatus.PENDING && to == OrderStatus.IN_PROGRESS) {
            order.setAssignedTo(actor);
        }
        Long assignedId = order.getAssignedTo() != null ? order.getAssignedTo().getId() : null;

        // Validación de transición legal + rol + asignación (400/403).
        statusTransitionValidator.validate(from, to, actor.getRole(), assignedId, actor.getId());

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

        // Notificaciones in-app + push por evento (US-05).
        notifyStatusChange(order, from, to, actor, request.comment());

        dashboardCache.invalidate();
        return response;
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getHistory(Long id) {
        // Valida que la orden pertenezca al workspace antes de exponer su historial.
        orderRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id));
        return historyRepository.findByOrderIdOrderByChangedAtDesc(id).stream()
                .map(h -> new StatusHistoryResponse(
                        h.getId(),
                        h.getOrderId(),
                        h.getFromStatus() != null ? h.getFromStatus().name() : null,
                        h.getToStatus() != null ? h.getToStatus().name() : null,
                        h.getComment(),
                        h.getChangedBy() != null ? h.getChangedBy().getId() : null,
                        h.getChangedBy() != null ? h.getChangedBy().getEmail() : null,
                        h.getChangedBy() != null ? h.getChangedBy().getRole().name() : null,
                        h.getChangedAt()))
                .toList();
    }

    private void notifyStatusChange(MaintenanceOrder order, OrderStatus from, OrderStatus to,
                                    User actor, String comment) {
        User manager = findManagerForOrder(order);
        User assigned = order.getAssignedTo();
        Long assignedId = assigned != null ? assigned.getId() : null;
        Long orderId = order.getId();
        String actorName = actor.getEmail();

        if (to == OrderStatus.IN_PROGRESS && from == OrderStatus.PENDING) {
            notify(manager, "ORDER_STARTED", "Orden #" + orderId + " iniciada por " + actorName, orderId,
                    "Orden iniciada", "Orden #" + orderId + " iniciada por " + actorName);
        } else if (to == OrderStatus.IN_REVIEW) {
            notify(manager, "ORDER_REVIEW", "Orden #" + orderId + " lista para revisión", orderId,
                    "Lista para revisión", "Orden #" + orderId + " lista para tu revisión");
        } else if (to == OrderStatus.COMPLETED) {
            notify(assigned, "ORDER_COMPLETED", "Orden #" + orderId + " aprobada y cerrada", orderId,
                    "Orden aprobada", "Orden #" + orderId + " fue aprobada y cerrada");
        } else if (to == OrderStatus.IN_PROGRESS && from == OrderStatus.IN_REVIEW) {
            notify(assigned, "ORDER_REJECTED", "Orden #" + orderId + " rechazada. Ver comentario", orderId,
                    "Orden rechazada", "Orden #" + orderId + " fue rechazada. Ver comentario");
        }
    }

    // Crea la notificación in-app y envía un mensaje por WebSocket si el usuario está conectado.
    private void notify(User recipient, String type, String message, Long orderId,
                        String pushTitle, String pushBody) {
        if (recipient == null) {
            return;
        }
        notificationService.createFor(recipient.getId(), type, message, orderId);
        // Canal específico del usuario: /queue/user-{id}
        messagingTemplate.convertAndSend("/queue/user-" + recipient.getId(),
                (Object) java.util.Map.of("title", pushTitle, "body", pushBody, "orderId", orderId));
    }

    // El supervisor es el MANAGER del workspace del técnico asignado a la orden.
    private User findManagerForOrder(MaintenanceOrder order) {
        Long workspaceId = order.getWorkspaceId();
        if (workspaceId == null) return null;
        return userRepository.findFirstByRoleAndWorkspaceIdOrderByIdAsc(Role.MANAGER, workspaceId).orElse(null);
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
        MaintenanceOrder order = orderRepository.findByIdAndWorkspaceId(id, wsId())
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id));

        // Una orden cerrada ya descontó (o nunca descontará) stock: no se le agregan piezas.
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("No se pueden modificar piezas de una orden cerrada.");
        }

        order.addPart(buildOrderPart(new CreateOrderRequest.PartInput(
                request.partId(), request.quantity(),
                request.externalPartName(), request.externalPartNumber(), request.externalUnitPrice())));
        return toResponse(orderRepository.save(order));
    }

    // US-10: quitar una pieza de una orden (solo antes de completarse).
    @Transactional
    public OrderResponseDTO removePart(Long orderId, Long partId) {
        MaintenanceOrder order = orderRepository.findByIdAndWorkspaceId(orderId, wsId())
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + orderId));

        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("No se pueden modificar piezas de una orden cerrada.");
        }

        order.getParts().removeIf(op -> op.getId().equals(partId));
        return toResponse(orderRepository.save(order));
    }

    @Transactional
    public OrderResponseDTO addPhotos(Long id, List<MultipartFile> photos) {
        MaintenanceOrder order = orderRepository.findByIdAndWorkspaceId(id, wsId())
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
            String fileName = UploadFiles.safeImageFileName(photo);
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
        int quantity = input.quantity() != null ? input.quantity() : 1;
        if (quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0.");
        }
        OrderPart op = new OrderPart();
        op.setQuantity(quantity);

        if (input.partId() != null) {
            SparePart part = sparePartRepository.findByIdAndWorkspaceId(input.partId(), wsId())
                    .orElseThrow(() -> new NoSuchElementException("Pieza no encontrada con id " + input.partId()));
            op.setSparePart(part);
        } else {
            if (blankToNull(input.externalPartName()) == null && blankToNull(input.externalPartNumber()) == null) {
                throw new IllegalArgumentException("Una pieza externa necesita nombre o número de parte.");
            }
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
