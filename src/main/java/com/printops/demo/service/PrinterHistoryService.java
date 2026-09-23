// src/main/java/com/printops/demo/service/PrinterHistoryService.java
package com.printops.demo.service;

import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.repository.MaintenanceOrderRepository;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

// Historial de mantenimiento por impresora (US-07). Expone métricas agregadas
// (intervenciones, costo, MTBF, pieza más reemplazada) y el timeline paginado.
@Service
public class PrinterHistoryService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final MaintenanceOrderRepository orderRepository;
    private final PrinterRepository printerRepository;

    public PrinterHistoryService(MaintenanceOrderRepository orderRepository,
                                 PrinterRepository printerRepository) {
        this.orderRepository = orderRepository;
        this.printerRepository = printerRepository;
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    // ── Historial paginado con filtros ───────────────────────────────────────
    @Transactional(readOnly = true)
    public PrinterHistoryDTO getHistory(Long printerId, HistoryFilters filters) {
        Printer printer = printerRepository.findByIdAndWorkspaceId(printerId, wsId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada con id " + printerId));

        List<MaintenanceOrder> all = loadOrders(printerId, filters);
        int totalElements = all.size();
        int size = filters.size() > 0 ? filters.size() : 20;
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int page = Math.max(0, filters.page());

        List<OrderHistoryItemDTO> items = all.stream()
                .skip((long) page * size)
                .limit(size)
                .map(this::toHistoryItem)
                .toList();

        return new PrinterHistoryDTO(toSummary(printer), calculateMetrics(printerId), items, totalPages, page);
    }

    private List<MaintenanceOrder> loadOrders(Long printerId, HistoryFilters f) {
        boolean hasType = f.type() != null;
        boolean hasDate = f.from() != null || f.to() != null;

        if (!hasType && !hasDate) {
            return orderRepository.findByPrinterIdAndWorkspaceIdOrderByCreatedAtDesc(printerId, wsId());
        }

        Instant fromI = null;
        Instant toI = null;
        if (hasDate) {
            LocalDate from = f.from() != null ? f.from() : LocalDate.of(1970, 1, 1);
            LocalDate to = f.to() != null ? f.to() : LocalDate.now();
            fromI = from.atStartOfDay(ZONE).toInstant();
            toI = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        }

        if (hasType && hasDate) {
            return orderRepository.findByPrinterIdAndWorkspaceIdAndTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
                    printerId, wsId(), f.type(), fromI, toI);
        }
        if (hasType) {
            return orderRepository.findByPrinterIdAndWorkspaceIdAndTypeOrderByCreatedAtDesc(printerId, wsId(), f.type());
        }
        return orderRepository.findByPrinterIdAndWorkspaceIdAndCreatedAtBetweenOrderByCreatedAtDesc(printerId, wsId(), fromI, toI);
    }

    // ── Métricas agregadas ───────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PrinterMetricsDTO getMetrics(Long printerId) {
        return calculateMetrics(printerId);
    }

    private PrinterMetricsDTO calculateMetrics(Long printerId) {
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

        Double mtbf = computeMtbf(orderRepository.getCorrectiveOrderDates(printerId, wsId()));

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

    // Tiempo medio entre fallas: promedio de días entre correctivas COMPLETED consecutivas.
    private Double computeMtbf(List<Instant> dates) {
        if (dates == null || dates.size() < 2) {
            return null;
        }
        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            intervals.add(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)));
        }
        double avg = intervals.stream().mapToLong(Long::longValue).average().orElse(0.0);
        return round(avg);
    }

    // ── Uso de piezas (para el endpoint /history/parts) ──────────────────────
    @Transactional(readOnly = true)
    public List<PartUsageSummaryDTO> getPartsUsage(Long printerId) {
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

        List<String> photos = o.getPhotos().stream().map(OrderPhoto::getUrl).toList();

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
