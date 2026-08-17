// src/main/java/com/printops/demo/service/OrderService.java
package com.printops.demo.service;

import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.repository.MaintenanceOrderRepository;
import com.printops.demo.repository.PartRepository;
import com.printops.demo.repository.PrinterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_PHOTOS_PER_ORDER = 5;

    private final MaintenanceOrderRepository orderRepository;
    private final PrinterRepository printerRepository;
    private final PartRepository partRepository;
    private final NotificationService notificationService;
    private final String uploadDir = "uploads/orders/";

    public OrderService(MaintenanceOrderRepository orderRepository,
                        PrinterRepository printerRepository,
                        PartRepository partRepository,
                        NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.printerRepository = printerRepository;
        this.partRepository = partRepository;
        this.notificationService = notificationService;
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo crear el directorio de uploads de órdenes.", e);
        }
    }

    @Transactional
    public OrderResponseDTO create(CreateOrderRequest dto) {
        Printer printer = printerRepository.findById(dto.printerId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con id " + dto.printerId()));

        OrderType type = parseType(dto.type());

        // Si la impresora está FUERA DE SERVICIO, la orden debe ser Correctivo.
        if (printer.getStatus() == PrinterStatus.OUT_OF_SERVICE && type != OrderType.CORRECTIVE) {
            throw new IllegalArgumentException("La impresora está fuera de servicio: la orden debe ser Correctivo.");
        }

        // En Correctivo la descripción del problema es obligatoria.
        if (type == OrderType.CORRECTIVE && (dto.description() == null || dto.description().isBlank())) {
            throw new IllegalArgumentException("La descripción del problema es obligatoria para órdenes correctivas.");
        }

        MaintenanceOrder order = new MaintenanceOrder();
        order.setPrinter(printer);
        order.setType(type);
        order.setStatus(OrderStatus.PENDING); // siempre se crea en Pending
        order.setDescription(blankToNull(dto.description()));
        order.setEstimatedTimeMinutes(dto.estimatedTimeMinutes());

        // Checklist
        if (dto.checklistItems() != null) {
            for (CreateOrderRequest.ChecklistItemInput item : dto.checklistItems()) {
                OrderChecklistItem ci = new OrderChecklistItem();
                ci.setText(item.text());
                ci.setDone(item.done());
                ci.setNa(item.na());
                order.addChecklistItem(ci);
            }
        }

        // Piezas
        if (dto.parts() != null) {
            for (CreateOrderRequest.PartInput input : dto.parts()) {
                order.addPart(buildOrderPart(input));
            }
        }

        MaintenanceOrder saved = orderRepository.save(order);

        // Notificación al supervisor/admin.
        notificationService.create(
                "Nueva orden de " + type + " creada para la impresora " + printer.getSerialNumber(),
                saved.getId());

        log.info("Orden creada id={} tipo={} impresora={}", saved.getId(), type, printer.getSerialNumber());
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
        return orders.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponseDTO get(Long id) {
        return toResponse(orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id)));
    }

    @Transactional
    public OrderResponseDTO updateStatus(Long id, UpdateOrderStatusRequest request, String role) {
        MaintenanceOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id));

        OrderStatus to = parseStatus(request.status());
        validateTransition(order.getStatus(), to, role);

        order.setStatus(to);
        if (request.actualTimeMinutes() != null) {
            order.setActualTimeMinutes(request.actualTimeMinutes());
        }
        return toResponse(orderRepository.save(order));
    }

    // Reglas de transición por rol (US de revisión):
    //  - TECNICO: PENDING↔IN_PROGRESS, IN_PROGRESS→IN_REVIEW, REJECTED→IN_PROGRESS, y puede cancelar PENDING/IN_PROGRESS.
    //  - MANAGER: aprueba (IN_REVIEW→APPROVED) o rechaza (IN_REVIEW→REJECTED), y puede cancelar.
    private void validateTransition(OrderStatus from, OrderStatus to, String role) {
        boolean isManager = role != null && role.contains("MANAGER");

        boolean allowed;
        if (isManager) {
            allowed = switch (to) {
                case APPROVED -> from == OrderStatus.IN_REVIEW;
                case REJECTED -> from == OrderStatus.IN_REVIEW;
                case CANCELLED -> from == OrderStatus.PENDING || from == OrderStatus.IN_PROGRESS || from == OrderStatus.IN_REVIEW;
                default -> false;
            };
        } else {
            allowed = switch (to) {
                case IN_PROGRESS -> from == OrderStatus.PENDING || from == OrderStatus.REJECTED;
                case IN_REVIEW -> from == OrderStatus.IN_PROGRESS;
                case CANCELLED -> from == OrderStatus.PENDING || from == OrderStatus.IN_PROGRESS;
                default -> false;
            };
        }

        if (!allowed) {
            throw new IllegalArgumentException("Transición no permitida de " + from + " a " + to + " para tu rol.");
        }
    }

    @Transactional
    public OrderResponseDTO addPart(Long id, AddPartRequest request) {
        MaintenanceOrder order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Orden no encontrada con id " + id));

        order.addPart(buildOrderPart(new CreateOrderRequest.PartInput(
                request.partId(), request.partNumber(), request.quantity(), request.external())));
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
        int quantity = input.quantity() != null ? input.quantity() : 1;
        op.setQuantity(quantity);
        op.setExternal(input.external());

        if (input.partId() != null) {
            Part part = partRepository.findById(input.partId())
                    .orElseThrow(() -> new NoSuchElementException("Pieza no encontrada con id " + input.partId()));
            if (part.getStockQuantity() < quantity) {
                throw new IllegalArgumentException("Stock insuficiente para la pieza " + part.getName());
            }
            // Descontar stock al usar la pieza.
            part.setStockQuantity(part.getStockQuantity() - quantity);
            partRepository.save(part);
            op.setPart(part);
            op.setPartNumber(part.getPartNumber());
        } else {
            // Pieza externa: número de parte libre.
            op.setExternal(true);
            op.setPartNumber(blankToNull(input.partNumber()));
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
                .map(p -> new OrderResponseDTO.PartResponse(
                        p.getId(),
                        p.getPart() != null ? p.getPart().getId() : null,
                        p.getPartNumber(),
                        p.getPart() != null ? p.getPart().getName() : null,
                        p.getQuantity(),
                        p.isExternal()))
                .toList();

        List<OrderResponseDTO.PhotoResponse> photos = o.getPhotos().stream()
                .map(ph -> new OrderResponseDTO.PhotoResponse(ph.getId(), ph.getUrl(), ph.getLabel()))
                .toList();

        return new OrderResponseDTO(
                o.getId(),
                o.getPrinter() != null ? o.getPrinter().getId() : null,
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
