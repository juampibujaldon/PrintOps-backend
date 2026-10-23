package com.printops.demo.service;

import com.printops.demo.dto.FailurePredictionDTO;
import com.printops.demo.dto.ProfitabilityDTO;
import com.printops.demo.entity.*;
import com.printops.demo.repository.*;
import com.printops.demo.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

// Analítica del taller: predicción de fallas y rentabilidad por impresora.
@Service
public class AnalyticsService {

    static final double RISK_SCHEDULE = 80.0;
    static final double WEAR_SCHEDULE = 85.0;
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final List<QuoteStatus> WON = List.of(QuoteStatus.ACCEPTED, QuoteStatus.PAID);

    private final JdbcTemplate jdbc;
    private final PrinterRepository printerRepository;
    private final QuoteRepository quoteRepository;
    private final OrderPartRepository orderPartRepository;
    private final MaintenanceOrderRepository orderRepository;
    private final StatusHistoryRepository historyRepository;
    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final WearCalculator wearCalculator;
    private final NotificationService notificationService;
    private final DashboardCache dashboardCache;
    private final double defaultHourlyRate;

    public AnalyticsService(JdbcTemplate jdbc, PrinterRepository printerRepository, QuoteRepository quoteRepository,
                            OrderPartRepository orderPartRepository, MaintenanceOrderRepository orderRepository,
                            StatusHistoryRepository historyRepository, WorkspaceRepository workspaceRepository,
                            UserRepository userRepository, WearCalculator wearCalculator,
                            NotificationService notificationService, DashboardCache dashboardCache,
                            @Value("${app.metrics.operator-hourly-rate:20.0}") double defaultHourlyRate) {
        this.jdbc = jdbc;
        this.printerRepository = printerRepository;
        this.quoteRepository = quoteRepository;
        this.orderPartRepository = orderPartRepository;
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.wearCalculator = wearCalculator;
        this.notificationService = notificationService;
        this.dashboardCache = dashboardCache;
        this.defaultHourlyRate = defaultHourlyRate;
    }

    private Long wsId() {
        return TenantContext.getCurrentWorkspaceId();
    }

    // ── Predicción de fallas ────────────────────────────────────────────────
    private record FailureStats(int count, Double mtbfDays, LocalDate last) {
    }

    @Transactional(readOnly = true)
    public List<FailurePredictionDTO> predictions() {
        Long ws = wsId();
        Map<Long, FailureStats> stats = new HashMap<>();
        // Una sola consulta para todo el taller: cantidad, última falla y MTBF (días enteros
        // entre correctivas completadas consecutivas, mismo criterio que el historial).
        jdbc.query("""
                SELECT printer_id, COUNT(*) AS n, MAX(created_at) AS last_at, AVG(gap) AS mtbf FROM (
                    SELECT printer_id, created_at,
                           TRUNC(EXTRACT(EPOCH FROM (created_at - LAG(created_at)
                               OVER (PARTITION BY printer_id ORDER BY created_at))) / 86400) AS gap
                    FROM maintenance_orders
                    WHERE workspace_id = ? AND type = 'CORRECTIVE' AND status = 'COMPLETED'
                ) g GROUP BY printer_id
                """, rs -> {
            Timestamp last = rs.getTimestamp("last_at");
            double mtbf = rs.getDouble("mtbf");
            stats.put(rs.getLong("printer_id"), new FailureStats(rs.getInt("n"), rs.wasNull() ? null : mtbf,
                    last != null ? last.toInstant().atZone(ZONE).toLocalDate() : null));
        }, ws);
        Set<Long> openPreventive = new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT printer_id FROM maintenance_orders
                WHERE workspace_id = ? AND type = 'PREVENTIVE' AND status IN ('PENDING', 'IN_PROGRESS', 'IN_REVIEW')
                """, Long.class, ws));

        LocalDate today = LocalDate.now(ZONE);
        List<FailurePredictionDTO> out = new ArrayList<>();
        for (Printer p : printerRepository.findByWorkspaceId(ws)) {
            FailureStats s = stats.getOrDefault(p.getId(), new FailureStats(0, null, null));
            LocalDate next = null;
            Double risk = null;
            if (s.mtbfDays() != null && s.mtbfDays() > 0 && s.last() != null) {
                next = s.last().plusDays(Math.round(s.mtbfDays()));
                risk = round1(Duration.between(s.last().atStartOfDay(), today.atStartOfDay()).toDays() / s.mtbfDays() * 100.0);
            }
            // Componente más gastado (según horas de uso y vida útil).
            var wear = wearCalculator.calculate(0, FilamentType.PLA, p);
            String[] names = {"Boquilla", "Hotend", "Correas", "Rodamientos", "Cama caliente"};
            double[] values = {wear.nozzle().percent(), wear.hotend().percent(), wear.belts().percent(),
                    wear.bearings().percent(), wear.heatbed().percent()};
            int worst = 0;
            for (int i = 1; i < values.length; i++) if (values[i] > values[worst]) worst = i;

            boolean preventive = openPreventive.contains(p.getId());
            out.add(new FailurePredictionDTO(p.getId(), name(p), p.getStatus().name(), s.count(),
                    s.mtbfDays() != null ? round1(s.mtbfDays()) : null, s.last(), next, risk,
                    names[worst], round1(values[worst]), preventive,
                    recommendation(risk, values[worst], preventive, s.count())));
        }
        out.sort(Comparator.comparing((FailurePredictionDTO d) -> score(d)).reversed());
        return out;
    }

    static String recommendation(Double risk, double worstWear, boolean hasPreventive, int failures) {
        if (hasPreventive) return "Preventivo ya programado.";
        if (risk != null && risk >= 100) return "Superó el intervalo medio entre fallas: programá un preventivo ya.";
        if (risk != null && risk >= RISK_SCHEDULE) return "Se acerca a su próxima falla estimada: programá un preventivo.";
        if (worstWear >= 100) return "Hay un componente fuera de su vida útil: reemplazalo.";
        if (worstWear >= WEAR_SCHEDULE) return "Un componente está cerca del fin de su vida útil.";
        if (failures < 2) return "Sin historial suficiente para estimar fallas.";
        return "Sin acciones por ahora.";
    }

    private static double score(FailurePredictionDTO d) {
        double r = d.riskPercent() != null ? d.riskPercent() : 0;
        double w = d.worstComponentWear() != null ? d.worstComponentWear() : 0;
        return Math.max(r, w) - (d.hasOpenPreventive() ? 1000 : 0);
    }

    // Crea una orden preventiva pendiente para la impresora y avisa a los técnicos.
    @Transactional
    public Long schedulePreventive(Long printerId, String email) {
        Printer p = printerRepository.findByIdAndWorkspaceId(printerId, wsId())
                .orElseThrow(() -> new NoSuchElementException("Impresora no encontrada"));
        User manager = userRepository.findByEmail(email).orElse(null);
        MaintenanceOrder o = new MaintenanceOrder();
        o.setPrinter(p);
        o.setType(OrderType.PREVENTIVE);
        o.setStatus(OrderStatus.PENDING);
        o.setWorkspaceId(p.getWorkspaceId());
        o.setDescription("Mantenimiento preventivo sugerido por la predicción de fallas.");
        orderRepository.save(o);
        StatusHistory h = new StatusHistory();
        h.setOrderId(o.getId());
        h.setToStatus(OrderStatus.PENDING);
        h.setChangedBy(manager);
        historyRepository.save(h);
        for (User t : userRepository.findByRoleAndWorkspaceId(Role.TECNICO, p.getWorkspaceId())) {
            notificationService.createFor(t.getId(), "ORDER_CREATED",
                    "Nueva orden preventiva #" + o.getId() + " para " + name(p) + ".", o.getId());
        }
        dashboardCache.invalidate();
        return o.getId();
    }

    // ── Rentabilidad ────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ProfitabilityDTO profitability(LocalDate from, LocalDate to) {
        Long ws = wsId();
        Instant fromI = from.atStartOfDay(ZONE).toInstant();
        Instant toI = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        Workspace w = workspaceRepository.findById(ws).orElse(null);
        // Mano de obra de mantenimiento con la tarifa del taller (si la cargó) o la global.
        double rate = w != null && w.getDefaultOperatorHourlyRate() != null && w.getDefaultOperatorHourlyRate() > 0
                ? w.getDefaultOperatorHourlyRate() : defaultHourlyRate;

        Map<Long, Double> parts = toMap(orderPartRepository.partsCostByPrinter(ws, fromI, toI));
        Map<Long, Double> minutes = toMap(orderRepository.sumActualMinutesByPrinter(ws, fromI, toI));
        Map<Long, double[]> sales = new HashMap<>(); // count, hours, revenue, cost, energy
        double[] unassigned = new double[5];
        for (Quote q : quoteRepository.findWonInPeriod(ws, WON, from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            int units = q.getUnits() != null ? q.getUnits() : 1;
            // Presupuestos sin impresora: fila propia para que el total no los pierda.
            double[] a = q.getPrinter() == null ? unassigned : sales.computeIfAbsent(q.getPrinter().getId(), k -> new double[5]);
            a[0] += 1;
            a[1] += nz(q.getPrintingHours()) * units;
            a[2] += nz(q.getTotalPrice());
            a[3] += nz(q.getTotalCost());
            a[4] += nz(q.getEnergyCost()) * units;
        }

        List<ProfitabilityDTO.Row> rows = new ArrayList<>();
        for (Printer p : printerRepository.findByWorkspaceId(ws)) {
            double[] a = sales.getOrDefault(p.getId(), new double[5]);
            double maintenance = parts.getOrDefault(p.getId(), 0.0) + minutes.getOrDefault(p.getId(), 0.0) / 60.0 * rate;
            double profit = a[2] - a[3] - maintenance;
            rows.add(new ProfitabilityDTO.Row(p.getId(), name(p), (int) a[0], round2(a[1]), round2(a[2]), round2(a[3]),
                    round2(a[4]), round2(maintenance), round2(profit),
                    a[2] > 0 ? round1(profit / a[2] * 100.0) : null, a[1] > 0 ? round2(profit / a[1]) : 0.0));
        }
        rows.sort(Comparator.comparingDouble(ProfitabilityDTO.Row::profit).reversed());
        if (unassigned[0] > 0) {
            double profit = unassigned[2] - unassigned[3];
            rows.add(new ProfitabilityDTO.Row(null, "Sin impresora asignada", (int) unassigned[0], round2(unassigned[1]),
                    round2(unassigned[2]), round2(unassigned[3]), round2(unassigned[4]), 0.0, round2(profit),
                    unassigned[2] > 0 ? round1(profit / unassigned[2] * 100.0) : null,
                    unassigned[1] > 0 ? round2(profit / unassigned[1]) : 0.0));
        }
        double revenue = rows.stream().mapToDouble(ProfitabilityDTO.Row::revenue).sum();
        double cost = rows.stream().mapToDouble(ProfitabilityDTO.Row::productionCost).sum();
        double maint = rows.stream().mapToDouble(ProfitabilityDTO.Row::maintenanceCost).sum();
        double profit = revenue - cost - maint;
        int quotes = rows.stream().mapToInt(ProfitabilityDTO.Row::quotes).sum();
        return new ProfitabilityDTO(from, to, new ProfitabilityDTO.Totals(quotes, round2(revenue), round2(cost),
                round2(maint), round2(profit), revenue > 0 ? round1(profit / revenue * 100.0) : null), rows);
    }

    private static Map<Long, Double> toMap(List<Object[]> rows) {
        Map<Long, Double> m = new HashMap<>();
        for (Object[] r : rows) {
            if (r[0] != null && r[1] != null) m.put(((Number) r[0]).longValue(), ((Number) r[1]).doubleValue());
        }
        return m;
    }

    static String name(Printer p) {
        return p.getName() != null ? p.getName() : p.getModel();
    }

    private static double nz(Double d) {
        return d != null ? d : 0.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
