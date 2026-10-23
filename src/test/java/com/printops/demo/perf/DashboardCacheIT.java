package com.printops.demo.perf;

import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.service.*;
import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// El dashboard esta cacheado 5 min: toda escritura que cambia su contenido
// debe invalidar la cache (una vez confirmada la transaccion).
class DashboardCacheIT extends IntegrationTestBase {

    @Autowired GlobalMetricsService metricsService;
    @Autowired OrderService orderService;
    @Autowired SparePartService sparePartService;
    @Autowired MaintenanceRuleService ruleService;
    @Autowired QuoteService quoteService;
    @Autowired PrinterService printerService;
    @Autowired PrintJobService printJobService;

    private static final MetricsFilters PERIOD = new MetricsFilters(LocalDate.now().minusDays(3), LocalDate.now().plusDays(1));

    private DashboardMetricsDTO dashboard() {
        return metricsService.getDashboardMetrics(PERIOD);
    }

    @Test
    void dashboardIsServedFromCacheBetweenWrites() {
        World w = newWorld();
        loginAs(w.manager);
        dashboard();
        stats().clear();
        dashboard();
        assertThat(stats().getPrepareStatementCount()).isZero();
    }

    @Test
    void manualOrderCreationInvalidatesDashboard() {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "P");
        loginAs(w.tech);
        assertThat(dashboard().totalOrders()).isZero();

        orderService.create(new CreateOrderRequest(printer.getId(), "PREVENTIVE", null, 30, null, null), w.tech.getEmail());

        assertThat(dashboard().totalOrders()).isEqualTo(1);
    }

    @Test
    void sparePartWritesInvalidateDashboard() {
        World w = newWorld();
        loginAs(w.manager);
        assertThat(dashboard().lowStockParts()).isEmpty();

        sparePartService.createPart(new CreateSparePartRequest("Boquilla", "B-1", null, null, "Extrusion", 0, 5, 1.0, null), w.manager);

        assertThat(dashboard().lowStockParts()).hasSize(1);
    }

    @Test
    void ruleGeneratedOrderInvalidatesDashboard() {
        deactivateAllRules();
        World w = newWorld();
        addOrders(w, 1, 0);
        addTriggeredRules(w, 1);
        loginAs(w.manager);
        assertThat(dashboard().totalOrders()).isZero();

        ruleService.evaluateAllRules();

        assertThat(dashboard().totalOrders()).isEqualTo(1);
    }

    @Test
    void wearTriggeredCorrectiveOrderInvalidatesDashboard() {
        World w = newWorld();
        Printer printer = newPrinter(w.workspaceId, "P");
        loginAs(w.tech);
        // 10.000 h de impresion superan el 100% de vida util de todos los componentes.
        Long quoteId = quoteService.create(new CreateQuoteRequest(100.0, 0.05, FilamentType.PLA, 200.0, 0.1, 10_000.0,
                0.0, 0.0, 10.0, 30.0, 0.0, 1, printer.getId(), "Cliente", "Trabajo", null, null, null), w.tech.getEmail()).id();
        assertThat(dashboard().totalOrders()).isZero();

        // El uso real (y el desgaste) se registra al terminar el trabajo de impresión que
        // genera el presupuesto aceptado, no al enviarlo.
        quoteService.changeStatus(quoteId, QuoteStatus.SENT);
        assertThat(dashboard().totalOrders()).isZero();
        quoteService.changeStatus(quoteId, QuoteStatus.ACCEPTED);
        PrintJobDTO job = printJobService.list(true, printer.getId()).getFirst();
        printJobService.start(job.id());
        printJobService.finish(job.id(), null, w.tech.getEmail());

        assertThat(dashboard().totalOrders()).isGreaterThan(0);
    }

    @Test
    void creatingAPrinterInvalidatesDashboard() {
        World w = newWorld();
        newPrinter(w.workspaceId, "Existente");
        loginAs(w.manager);
        assertThat(dashboard().availabilityByPrinter()).hasSize(1);

        printerService.createPrinter(new CreatePrinterRequest("Bambu", "X1", "SN-NEW-" + w.workspaceId,
                LocalDate.of(2025, 1, 1), "OPERATIVE", "Nueva", null, null, null), null, w.manager.getEmail());

        assertThat(dashboard().availabilityByPrinter()).hasSize(2);
    }
}
