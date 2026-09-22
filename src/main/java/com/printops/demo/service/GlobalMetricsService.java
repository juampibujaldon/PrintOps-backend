// src/main/java/com/printops/demo/service/GlobalMetricsService.java
package com.printops.demo.service;

import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.repository.MaintenanceOrderRepository;
import com.printops.demo.repository.OrderPartRepository;
import com.printops.demo.repository.PrinterRepository;
import com.printops.demo.repository.SparePartRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// KPIs globales del parque de impresoras (US-08). Resultado cacheado 5 min
// (en producción con Caffeine) e invalidado al completarse una orden.
@Service
public class GlobalMetricsService {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final int MAX_MONTHS = 6;

    private final MaintenanceOrderRepository orderRepository;
    private final OrderPartRepository orderPartRepository;
    private final SparePartRepository sparePartRepository;
    private final PrinterRepository printerRepository;

    // Tarifa horaria del operario para calcular el costo de mano de obra.
    @Value("${app.metrics.operator-hourly-rate:20.0}")
    private double hourlyRate;

    public GlobalMetricsService(MaintenanceOrderRepository orderRepository,
                                OrderPartRepository orderPartRepository,
                                SparePartRepository sparePartRepository,
                                PrinterRepository printerRepository) {
        this.orderRepository = orderRepository;
        this.orderPartRepository = orderPartRepository;
        this.sparePartRepository = sparePartRepository;
        this.printerRepository = printerRepository;
    }

    @Cacheable(cacheNames = "dashboard-metrics")
    @Transactional(readOnly = true)
    public DashboardMetricsDTO getDashboardMetrics(MetricsFilters filters) {
        LocalDate from = filters.from();
        LocalDate to = filters.to();
        Instant fromI = from.atStartOfDay(ZONE).toInstant();
        Instant toI = to.plusDays(1).atStartOfDay(ZONE).toInstant();

        List<Printer> printers = printerRepository.findAll();

        // ── Órdenes ──────────────────────────────────────────────────────────
        Map<OrderStatus, Long> statusCounts = toStatusMap(orderRepository.countByStatusInPeriod(fromI, toI));
        Map<OrderType, Long> typeCounts = toTypeMap(orderRepository.countByTypeInPeriod(fromI, toI));

        int totalOrders = (int) statusCounts.values().stream().mapToLong(Long::longValue).sum();
        int openOrders = (int) (statusCounts.getOrDefault(OrderStatus.PENDING, 0L)
                + statusCounts.getOrDefault(OrderStatus.IN_PROGRESS, 0L)
                + statusCounts.getOrDefault(OrderStatus.IN_REVIEW, 0L));
        int closedOrders = statusCounts.getOrDefault(OrderStatus.COMPLETED, 0L).intValue();

        OrderTypeBreakdownDTO byType = new OrderTypeBreakdownDTO(
                typeCounts.getOrDefault(OrderType.PREVENTIVE, 0L).intValue(),
                typeCounts.getOrDefault(OrderType.CORRECTIVE, 0L).intValue(),
                typeCounts.getOrDefault(OrderType.CALIBRATION, 0L).intValue());

        double avgResolution = avgResolutionHours(orderRepository.completedTimestamps(fromI, toI));
        int overdue = (int) orderRepository.countOverdue(Instant.now().minus(7, ChronoUnit.DAYS));

        // ── Disponibilidad ───────────────────────────────────────────────────
        Map<Long, Double> minutesByPrinter = toLongDoubleMap(orderRepository.sumActualMinutesByPrinter(fromI, toI));
        double periodHours = (ChronoUnit.DAYS.between(from, to) + 1) * 24.0;

        List<PrinterAvailabilityDTO> availability = printers.stream()
                .map(p -> {
                    double minutes = minutesByPrinter.getOrDefault(p.getId(), 0.0);
                    double avail = periodHours > 0
                            ? ((periodHours - minutes / 60.0) / periodHours) * 100.0
                            : 100.0;
                    return new PrinterAvailabilityDTO(p.getId(), printerName(p), round(clamp(avail)));
                })
                .toList();

        double globalAvailability = availability.isEmpty()
                ? 100.0
                : availability.stream().mapToDouble(PrinterAvailabilityDTO::availabilityPercent).average().orElse(100.0);

        // ── Costos ───────────────────────────────────────────────────────────
        Double totalParts = orderPartRepository.totalPartsCostInPeriod(fromI, toI);
        if (totalParts == null) totalParts = 0.0;

        Long totalMinutes = orderRepository.totalActualMinutesInPeriod(fromI, toI);
        double totalLabor = (totalMinutes != null ? totalMinutes : 0L) / 60.0 * hourlyRate;
        double totalCost = totalParts + totalLabor;

        Map<Long, Double> partsByPrinter = toLongDoubleMap(orderPartRepository.partsCostByPrinter(fromI, toI));
        List<PrinterCostDTO> costByPrinter = printers.stream()
                .map(p -> {
                    double parts = partsByPrinter.getOrDefault(p.getId(), 0.0);
                    double labor = minutesByPrinter.getOrDefault(p.getId(), 0.0) / 60.0 * hourlyRate;
                    return new PrinterCostDTO(p.getId(), printerName(p), round(parts + labor));
                })
                .sorted(Comparator.comparingDouble(PrinterCostDTO::totalCost).reversed())
                .toList();

        Map<String, Double> partsByMonth = toMonthMap(orderPartRepository.partsCostByMonth(fromI, toI));
        Map<String, Double> laborByMonth = toMonthMap(orderRepository.laborMinutesByMonth(fromI, toI));
        List<MonthlyCostDTO> costByMonth = buildMonthlyCost(from, to, partsByMonth, laborByMonth);

        // ── Top fallas ───────────────────────────────────────────────────────
        Map<Long, Double> correctiveParts = toLongDoubleMap(orderPartRepository.correctivePartsCostByPrinter(fromI, toI));
        List<FailingPrinterDTO> top3Failing = orderRepository.topFailingPrinters(fromI, toI).stream()
                .limit(3)
                .map(row -> {
                    Long id = (Long) row[0];
                    String name = (String) row[1];
                    int count = ((Number) row[2]).intValue();
                    // totalCost = costo de piezas de sus correctivas COMPLETED.
                    return new FailingPrinterDTO(id, name, count, round(correctiveParts.getOrDefault(id, 0.0)));
                })
                .toList();

        // ── Stock bajo ───────────────────────────────────────────────────────
        List<LowStockPartDTO> lowStock = sparePartRepository.findByStockLessThanEqualMinStock().stream()
                .map(p -> new LowStockPartDTO(
                        p.getId(), p.getName(), p.getPartNumber(),
                        p.getStock() != null ? p.getStock() : 0,
                        p.getMinStock() != null ? p.getMinStock() : 0))
                .toList();

        return new DashboardMetricsDTO(
                round(globalAvailability), availability,
                round(totalCost), round(totalParts), round(totalLabor),
                costByMonth, costByPrinter,
                totalOrders, openOrders, closedOrders, byType,
                round(avgResolution), overdue,
                top3Failing, lowStock,
                from, to);
    }

    // ── Helpers de conversión ────────────────────────────────────────────────
    private Map<OrderStatus, Long> toStatusMap(List<Object[]> rows) {
        Map<OrderStatus, Long> map = new EnumMap<>(OrderStatus.class);
        for (Object[] row : rows) {
            map.put((OrderStatus) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    private Map<OrderType, Long> toTypeMap(List<Object[]> rows) {
        Map<OrderType, Long> map = new EnumMap<>(OrderType.class);
        for (Object[] row : rows) {
            map.put((OrderType) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    private Map<Long, Double> toLongDoubleMap(List<Object[]> rows) {
        Map<Long, Double> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], ((Number) row[1]).doubleValue());
        }
        return map;
    }

    private Map<String, Double> toMonthMap(List<Object[]> rows) {
        Map<String, Double> map = new HashMap<>();
        for (Object[] row : rows) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            double value = ((Number) row[2]).doubleValue();
            map.put(String.format("%04d-%02d", year, month), value);
        }
        return map;
    }

    private List<MonthlyCostDTO> buildMonthlyCost(LocalDate from, LocalDate to,
                                                  Map<String, Double> partsByMonth,
                                                  Map<String, Double> laborMinutesByMonth) {
        List<MonthlyCostDTO> result = new ArrayList<>();
        YearMonth cursor = YearMonth.from(to);
        YearMonth start = YearMonth.from(from);
        int count = 0;
        while (!cursor.isBefore(start) && count < MAX_MONTHS) {
            String key = cursor.toString();
            double parts = partsByMonth.getOrDefault(key, 0.0);
            double labor = laborMinutesByMonth.getOrDefault(key, 0.0) / 60.0 * hourlyRate;
            result.add(0, new MonthlyCostDTO(key, round(parts + labor)));
            cursor = cursor.minusMonths(1);
            count++;
        }
        return result;
    }

    private double avgResolutionHours(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) return 0.0;
        double sum = 0.0;
        int n = 0;
        for (Object[] row : rows) {
            Instant created = (Instant) row[0];
            Instant updated = (Instant) row[1];
            if (created != null && updated != null) {
                sum += ChronoUnit.MINUTES.between(created, updated) / 60.0;
                n++;
            }
        }
        return n == 0 ? 0.0 : sum / n;
    }

    private String printerName(Printer p) {
        return (p.getName() != null && !p.getName().isBlank()) ? p.getName() : p.getModel();
    }

    private double clamp(double value) {
        if (value < 0) return 0.0;
        if (value > 100) return 100.0;
        return value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
