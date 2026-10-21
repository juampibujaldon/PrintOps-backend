package com.printops.demo.perf;

import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.service.*;
import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Tests de caracterizacion: fijan el comportamiento observable (contrato de respuesta)
// ANTES de optimizar, para que el refactor a SQL no cambie resultados.
class BehaviorIT extends IntegrationTestBase {

    @Autowired OrderService orderService;
    @Autowired SparePartService sparePartService;
    @Autowired PrinterHistoryService historyService;
    @Autowired GlobalMetricsService metricsService;

    // ── Ordenes: IN_REVIEW primero, luego createdAt descendente; filtros ─────
    @Test
    void orderList_putsInReviewFirst_thenNewestFirst() {
        World w = newWorld();
        addOrders(w, 2, 6); // statuses rotan PENDING, IN_PROGRESS, IN_REVIEW, COMPLETED
        loginAs(w.manager);

        List<OrderResponseDTO> list = orderService.list(null, null);

        assertThat(list).hasSize(12);
        int firstNonReview = 0;
        while (firstNonReview < list.size() && list.get(firstNonReview).status().equals("IN_REVIEW")) firstNonReview++;
        assertThat(firstNonReview).isEqualTo(3); // n=2,6,10
        assertThat(list.subList(firstNonReview, list.size())).noneMatch(o -> o.status().equals("IN_REVIEW"));
        // dentro de cada grupo: createdAt descendente
        for (int i = 1; i < firstNonReview; i++) {
            assertThat(list.get(i - 1).createdAt()).isAfterOrEqualTo(list.get(i).createdAt());
        }
        for (int i = firstNonReview + 1; i < list.size(); i++) {
            assertThat(list.get(i - 1).createdAt()).isAfterOrEqualTo(list.get(i).createdAt());
        }
    }

    @Test
    void orderList_filtersByPrinterAndStatus() {
        World w = newWorld();
        addOrders(w, 2, 4);
        loginAs(w.manager);
        Long printerId = w.printers.get(0).getId();

        assertThat(orderService.list(printerId, null)).hasSize(4)
                .allMatch(o -> o.printerId().equals(printerId));
        assertThat(orderService.list(null, "COMPLETED")).hasSize(2)
                .allMatch(o -> o.status().equals("COMPLETED"));
        assertThat(orderService.list(printerId, "PENDING")).hasSize(1);
        assertThat(orderService.list(null, null)).hasSize(8);
    }

    @Test
    void orderList_doesNotLeakOtherWorkspaces() {
        World a = newWorld();
        World b = newWorld();
        addOrders(a, 1, 3);
        addOrders(b, 1, 2);
        loginAs(a.manager);
        assertThat(orderService.list(null, null)).hasSize(3);
    }

    @Test
    void orderResponse_keepsShape() {
        World w = newWorld();
        addParts(w, 5);
        addOrders(w, 1, 1);
        loginAs(w.manager);

        OrderResponseDTO dto = orderService.get(w.orderIds.get(0));

        assertThat(dto.assignedToName()).isEqualTo(w.tech.getEmail());
        assertThat(dto.checklistItems()).hasSize(3);
        assertThat(dto.photos()).hasSize(2);
        assertThat(dto.parts()).hasSize(3);
        assertThat(dto.parts()).filteredOn(OrderResponseDTO.PartResponse::external).hasSize(1);
    }

    // ── Repuestos: busqueda, categoria y stock bajo ──────────────────────────
    @Test
    void sparePartList_filters() {
        World w = newWorld();
        addParts(w, 40);
        loginAs(w.manager);

        assertThat(sparePartService.list(null, null, false)).hasSize(40);
        assertThat(sparePartService.list("  BOQUILLA ", null, false)).hasSize(8);       // case-insensitive + trim
        assertThat(sparePartService.list("pn-" + w.workspaceId + "-7", null, false)).hasSize(1); // por numero de parte
        assertThat(sparePartService.list(null, "extrusion", false)).hasSize(10);        // categoria sin mayusculas
        assertThat(sparePartService.list(null, " ", false)).hasSize(40);                // categoria en blanco = sin filtro
        assertThat(sparePartService.list("boquilla", "Extrusion", true)).hasSize(2);   // 0,20 -> i%5==0 && i%4==0
        assertThat(sparePartService.list(null, null, true)).hasSize(10);
        assertThat(sparePartService.list("%", null, false)).isEmpty();                  // comodines literales
        assertThat(sparePartService.list("_", null, false)).isEmpty();
        assertThat(sparePartService.list(null, null, false)).isSortedAccordingTo(
                (a, b) -> a.name().compareTo(b.name()));
    }

    // ── Historial por impresora ──────────────────────────────────────────────
    @Test
    void history_paginationFiltersAndOrdering() {
        World w = newWorld();
        addParts(w, 10);
        addOrders(w, 1, 20); // tipos rotan PREVENTIVE, CORRECTIVE, CALIBRATION
        loginAs(w.manager);
        Long printerId = w.printers.get(0).getId();

        PrinterHistoryDTO p0 = historyService.getHistory(printerId, new HistoryFilters(null, null, null, 0, 8));
        PrinterHistoryDTO p2 = historyService.getHistory(printerId, new HistoryFilters(null, null, null, 2, 8));
        PrinterHistoryDTO p9 = historyService.getHistory(printerId, new HistoryFilters(null, null, null, 9, 8));

        assertThat(p0.orders()).hasSize(8);
        assertThat(p0.totalPages()).isEqualTo(3);
        assertThat(p0.currentPage()).isZero();
        assertThat(p2.orders()).hasSize(4);
        assertThat(p9.orders()).isEmpty();
        // mas reciente primero: la orden n=0 fue creada 1h atras, la n=1 hace 2h, ...
        assertThat(p0.orders().get(0).id()).isEqualTo(w.orderIds.get(0));
        assertThat(p0.orders().get(1).id()).isEqualTo(w.orderIds.get(1));
        assertThat(p0.orders().get(0).partsUsed()).hasSize(3);
        assertThat(p0.orders().get(0).photoUrls()).hasSize(2);
        assertThat(p0.orders().get(0).technicianName()).isEqualTo(w.tech.getEmail());

        PrinterHistoryDTO corrective = historyService.getHistory(printerId,
                new HistoryFilters(OrderType.CORRECTIVE, null, null, 0, 50));
        assertThat(corrective.orders()).hasSize(7).allMatch(o -> o.type().equals("CORRECTIVE"));

        PrinterHistoryDTO today = historyService.getHistory(printerId,
                new HistoryFilters(null, LocalDate.now().minusDays(1), LocalDate.now(), 0, 50));
        assertThat(today.orders()).hasSize(20);
        PrinterHistoryDTO future = historyService.getHistory(printerId,
                new HistoryFilters(null, LocalDate.now().plusDays(1), null, 0, 50));
        assertThat(future.orders()).isEmpty();
        PrinterHistoryDTO both = historyService.getHistory(printerId,
                new HistoryFilters(OrderType.PREVENTIVE, LocalDate.now().minusDays(1), LocalDate.now(), 0, 50));
        assertThat(both.orders()).hasSize(7).allMatch(o -> o.type().equals("PREVENTIVE"));

        PrinterHistoryDTO none = historyService.getHistory(printerId, new HistoryFilters(null, null, null, 0, 0));
        assertThat(none.orders()).hasSize(20); // size <= 0 => 20 por defecto
    }

    @Test
    void history_mtbfIsAverageOfTruncatedDayIntervals() {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "MTBF");
        w.printers.add(printer);
        // correctivas COMPLETED: t1 = -30d, t2 = t1 + 9d19h (9 dias), t3 = t2 + 15d2h (15 dias) -> MTBF 12.0
        long[] hoursAgo = {30 * 24, 30 * 24 - (9 * 24 + 19), 30 * 24 - (9 * 24 + 19) - (15 * 24 + 2)};
        for (long h : hoursAgo) {
            MaintenanceOrder o = new MaintenanceOrder();
            o.setPrinter(printer);
            o.setType(OrderType.CORRECTIVE);
            o.setStatus(OrderStatus.COMPLETED);
            o.setWorkspaceId(w.workspaceId);
            Long id = orderRepository.save(o).getId();
            jdbc.update("UPDATE maintenance_orders SET created_at = now() - make_interval(hours => ?) WHERE id = ?", (int) h, id);
        }
        loginAs(w.manager);

        PrinterMetricsDTO metrics = historyService.getMetrics(printer.getId());

        assertThat(metrics.mtbfDays()).isEqualTo(12.0);
        assertThat(metrics.correctiveCount()).isEqualTo(3);
    }

    @Test
    void history_mtbfIsNullWithLessThanTwoCorrectives() {
        World w = newWorld();
        addOrders(w, 1, 1);
        loginAs(w.manager);
        assertThat(historyService.getMetrics(w.printers.get(0).getId()).mtbfDays()).isNull();
    }

    // ── Dashboard: promedio de resolucion ────────────────────────────────────
    @Test
    void dashboard_avgResolutionHours_matchesTruncatedMinutes() {
        World w = newWorld();
        addOrders(w, 1, 20);
        loginAs(w.manager);

        // COMPLETED = n%4==3; resolucion = (n%5+1)*90 min (+30s que se trunca)
        double sumMinutes = 0;
        int n = 0;
        for (int i = 0; i < 20; i++) {
            if (i % 4 == 3) { sumMinutes += (i % 5 + 1) * 90; n++; }
        }
        double expected = Math.round((sumMinutes / n / 60.0) * 100.0) / 100.0;

        DashboardMetricsDTO dto = metricsService.getDashboardMetrics(
                new MetricsFilters(LocalDate.now().minusDays(3), LocalDate.now()));

        assertThat(dto.closedOrders()).isEqualTo(5);
        assertThat(dto.avgResolutionHours()).isEqualTo(expected);
    }

    @Test
    void dashboard_avgResolutionHours_isZeroWithoutCompletedOrders() {
        World w = newWorld();
        loginAs(w.manager);
        DashboardMetricsDTO dto = metricsService.getDashboardMetrics(
                new MetricsFilters(LocalDate.now().minusDays(3), LocalDate.now()));
        assertThat(dto.avgResolutionHours()).isZero();
    }
}
