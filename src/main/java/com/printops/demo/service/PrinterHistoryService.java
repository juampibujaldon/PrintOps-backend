// src/main/java/com/printops/demo/service/PrinterHistoryService.java
package com.printops.demo.service;

import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.repository.MaintenanceOrderRepository;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.security.TenantContext;
import com.printops.demo.storage.FileStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;

// Historial de mantenimiento por impresora (US-07). Expone métricas agregadas
// (intervenciones, costo, MTBF, pieza más reemplazada) y el timeline paginado.
@Service
public class PrinterHistoryService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int DEFAULT_PAGE_SIZE = 20;
    // Tope de tamano de pagina: evita que un cliente pida el historial completo de una vez.
    static final int MAX_PAGE_SIZE = 100;
    // Limites abiertos cuando no hay filtro de fechas (el parametro no puede ser null).
    private static final Instant MIN_INSTANT = Instant.EPOCH;
    private static final Instant MAX_INSTANT = Instant.parse("9999-12-31T00:00:00Z");

    private final MaintenanceOrderRepository orderRepository;
    private final PrinterRepository printerRepository;
    private final FileStorage fileStorage;

    public PrinterHistoryService(MaintenanceOrderRepository orderRepository,
                                 PrinterRepository printerRepository,
                                 FileStorage fileStorage) {
        this.orderRepository = orderRepository;
        this.printerRepository = printerRepository;
        this.fileStorage = fileStorage;
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    // ── Historial paginado con filtros ───────────────────────────────────────
    @Transactional(readOnly = true)
    public PrinterHistoryDTO getHistory(Long printerId, HistoryFilters filters) {
        Printer printer = printerRepository.findByIdAndWorkspaceId(printerId, wsId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con id " + printerId));

        int size = filters.size() > 0 ? Math.min(filters.size(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        int page = Math.max(0, filters.page());

        Page<MaintenanceOrder> result = loadPage(printerId, filters, PageRequest.of(page, size));
        List<OrderHistoryItemDTO> items = result.getContent().stream().map(this::toHistoryItem).toList();

        return new PrinterHistoryDTO(toSummary(printer), calculateMetrics(printerId), items, result.getTotalPages(), page);
    }

    private Page<MaintenanceOrder> loadPage(Long printerId, HistoryFilters f, PageRequest pageable) {
        Instant fromI = MIN_INSTANT;
        Instant toI = MAX_INSTANT;
        if (f.from() != null || f.to() != null) {
            LocalDate from = f.from() != null ? f.from() : LocalDate.of(1970, 1, 1);
            LocalDate to = f.to() != null ? f.to() : LocalDate.now();
            fromI = from.atStartOfDay(ZONE).toInstant();
            toI = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        }
        return orderRepository.findHistory(printerId, wsId(), f.type(), fromI, toI, pageable);
    }

    // ── Métricas agregadas ───────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PrinterMetricsDTO getMetrics(Long printerId) {
        requireOwnPrinter(printerId);
        return calculateMetrics(printerId);
    }

    private void requireOwnPrinter(Long printerId) {
        printerRepository.findByIdAndWorkspaceId(printerId, wsId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con id " + printerId));
    }

    public PrinterMetricsDTO calculateMetrics(Long printerId) {
        int totalInterventions = (int) orderRepository.countByPrinterIdAndWorkspaceIdAndStatus(printerId, wsId(), OrderStatus.COMPLETED);

        Double totalCost = orderRepository.getTotalPartsCostByPrinter(printerId, wsId());
        if (totalCost == null) totalCost = 0.0;

        int preventive = 0, corrective = 0, calibration = 0;
        for (Object[] row : orderRepository.countByTypeForPrinter(printerId, wsId())) {
            OrderType type = (OrderType) row[0];
            int count = ((Number) row[1]).intValue();
            switch (type) {
                case PREVENTIVE -> preventive = count;
                case CORRECTIVE -> corrective = count;
                case CALIBRATION -> calibration = count;
            }
        }

        Double mtbf = orderRepository.averageDaysBetweenCorrectives(printerId, wsId());
        if (mtbf != null) mtbf = round(mtbf);

        String mostPart = null;
        int mostCount = 0;
        List<Object[]> usage = orderRepository.getPartUsageSummary(printerId, wsId());
        if (!usage.isEmpty()) {
            Object[] first = usage.get(0);
            mostPart = (String) first[1];
            mostCount = ((Number) first[3]).intValue();
        }

        return new PrinterMetricsDTO(
                totalInterventions, round(totalCost), preventive, corrective, calibration,
                mtbf, mostPart, mostCount);
    }

    // ── Uso de piezas (para el endpoint /history/parts) ──────────────────────
    @Transactional(readOnly = true)
    public List<PartUsageSummaryDTO> getPartsUsage(Long printerId) {
        requireOwnPrinter(printerId);
        return orderRepository.getPartUsageSummary(printerId, wsId()).stream()
                .map(row -> new PartUsageSummaryDTO(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        ((Number) row[3]).intValue(),
                        round(((Number) row[4]).doubleValue())))
                .toList();
    }

    // ── Mapeos ───────────────────────────────────────────────────────────────
    private OrderHistoryItemDTO toHistoryItem(MaintenanceOrder o) {
        List<OrderHistoryItemDTO.PartUsedDTO> parts = o.getParts().stream()
                .map(op -> {
                    SparePart sp = op.getSparePart();
                    boolean external = sp == null;
                    double unit = external
                            ? (op.getExternalUnitPrice() != null ? op.getExternalUnitPrice() : 0.0)
                            : (sp.getUnitPrice() != null ? sp.getUnitPrice() : 0.0);
                    String number = external ? op.getExternalPartNumber() : sp.getPartNumber();
                    String name = external ? op.getExternalPartName() : sp.getName();
                    return new OrderHistoryItemDTO.PartUsedDTO(
                            sp != null ? sp.getId() : null,
                            name != null ? name : number,
                            number,
                            op.getQuantity(),
                            round(unit * op.getQuantity()));
                })
                .toList();

        double partsCost = parts.stream().mapToDouble(OrderHistoryItemDTO.PartUsedDTO::cost).sum();

        List<String> photos = o.getPhotos().stream().map(ph -> fileStorage.urlFor(ph.getStorageKey())).toList();

        String technician = o.getAssignedTo() != null ? o.getAssignedTo().getEmail() : null;

        boolean terminal = o.getStatus() == OrderStatus.COMPLETED || o.getStatus() == OrderStatus.CANCELLED;
        Instant closedAt = terminal ? o.getUpdatedAt() : null;

        return new OrderHistoryItemDTO(
                o.getId(),
                String.format("ORD-%05d", o.getId()),
                o.getType() != null ? o.getType().name() : null,
                o.getStatus() != null ? o.getStatus().name() : null,
                o.getDescription(),
                o.getCreatedAt(),
                closedAt,
                o.getActualTimeMinutes(),
                technician,
                parts,
                photos,
                round(partsCost));
    }

    private PrinterSummaryDTO toSummary(Printer p) {
        return new PrinterSummaryDTO(p.getId(), p.getName(), p.getBrand(), p.getModel(), p.getSerialNumber());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
