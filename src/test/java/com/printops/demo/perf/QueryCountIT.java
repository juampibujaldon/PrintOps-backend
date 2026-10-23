package com.printops.demo.perf;

import com.printops.demo.dto.*;
import com.printops.demo.entity.*;
import com.printops.demo.service.*;
import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

// Cuenta sentencias SQL (Hibernate Statistics) de los caminos calientes con
// datos realistas: 20 ordenes (3 checklist + 3 piezas + 2 fotos c/u), 30 reglas.
// Los topes son el objetivo de la optimizacion: rojo = por encima del objetivo.
class QueryCountIT extends IntegrationTestBase {

    @Autowired OrderService orderService;
    @Autowired SparePartService sparePartService;
    @Autowired PrinterHistoryService historyService;
    @Autowired QuoteService quoteService;
    @Autowired MaintenanceRuleService ruleService;

    private World fullWorld() {
        World w = newWorld();
        addParts(w, 40);
        addOrders(w, 5, 4);
        return w;
    }

    @Test
    void orderList_hasConstantStatementCount() {
        World w = fullWorld();
        loginAs(w.manager);

        var m = measure("OrderService.list (20 orders)", () -> orderService.list(null, null));

        assertThat(m.result()).hasSize(20);
        assertThat(m.m().statements()).isLessThanOrEqualTo(5);
    }

    @Test
    void orderGet_hasConstantStatementCount() {
        World w = fullWorld();
        loginAs(w.manager);

        var m = measure("OrderService.get (1 order)", () -> orderService.get(w.orderIds.get(0)));

        assertThat(m.result().parts()).hasSize(3);
        assertThat(m.m().statements()).isLessThanOrEqualTo(5);
    }

    @Test
    void sparePartList_filtersInSql_notInJava() {
        World w = fullWorld();
        loginAs(w.manager);

        var m = measure("SparePartService.list lowStock (40 parts, 10 low)",
                () -> sparePartService.list(null, null, true));

        assertThat(m.result()).hasSize(10);
        assertThat(m.m().statements()).isEqualTo(1);
        // Antes: se cargaban las 40 piezas y se filtraba en Java.
        assertThat(entityLoads(SparePart.class)).isEqualTo(10);
    }

    @Test
    void printerHistory_pagesInDatabase() {
        World w = newWorld();
        addParts(w, 40);
        addOrders(w, 1, 20);
        loginAs(w.manager);
        Long printerId = w.printers.get(0).getId();

        var m = measure("PrinterHistoryService.getHistory page 0 size 5 (20 orders)",
                () -> historyService.getHistory(printerId, new HistoryFilters(null, null, null, 0, 5)));

        assertThat(m.result().orders()).hasSize(5);
        assertThat(m.result().totalPages()).isEqualTo(4);
        assertThat(m.m().statements()).isLessThanOrEqualTo(11);
        // Antes: se cargaban las 20 ordenes y se paginaba en memoria.
        assertThat(entityLoads(MaintenanceOrder.class)).isLessThanOrEqualTo(5);
    }

    @Test
    void printerHistory_capsPageSizeAt100() {
        World w = newWorld();
        addOrders(w, 1, 120);
        loginAs(w.manager);

        PrinterHistoryDTO dto = historyService.getHistory(w.printers.get(0).getId(),
                new HistoryFilters(null, null, null, 0, 1000));

        assertThat(dto.orders()).hasSize(100);
        assertThat(dto.totalPages()).isEqualTo(2);
    }

    @Test
    void quoteList_fetchesPrinterInSameQuery() {
        World w = fullWorld();
        loginAs(w.tech);
        for (int i = 0; i < 10; i++) {
            quoteService.create(new CreateQuoteRequest(100.0, 0.05, FilamentType.PLA, 200.0, 0.1, 5.0,
                    0.0, 0.0, 10.0, 30.0, 0.0, 1, w.printers.get(i % 5).getId(), "Cliente " + i, "Trabajo", null, null, null), w.tech.getEmail());
        }

        var m = measure("QuoteService.list (10 quotes, 5 printers)",
                () -> quoteService.list(null, null, null, null, null));

        assertThat(m.result()).hasSize(10);
        assertThat(m.result().get(0).printerName()).isNotNull();
        // 1 consulta con la impresora + 1 en lote para los gastos extra de todos (@BatchSize):
        // constante, no crece con la cantidad de presupuestos.
        assertThat(m.m().statements()).isEqualTo(2);
    }

    @Test
    void ruleEvaluation_scalesWithoutPerRuleLookups() {
        deactivateAllRules();
        World w = newWorld();
        addOrders(w, 5, 1);
        addTriggeredRules(w, 30);

        var m = measure("MaintenanceRuleService.evaluateAllRules (30 triggered rules, 2 technicians)",
                () -> { ruleService.evaluateAllRules(); return null; });

        assertThat(orderRepository.findByWorkspaceId(w.workspaceId).stream()
                .filter(o -> o.getMaintenanceRuleId() != null).count()).isEqualTo(30);
        // Por regla disparada: exists + orden + 2 checklist + update de la regla + 2 notificaciones = 7.
        // Mas 1 (reglas con impresora) + 1 (tecnicos del workspace, una sola vez).
        assertThat(m.m().statements()).isLessThanOrEqualTo(30 * 7 + 2);
    }

    @Test
    void completingOrder_doesNotRequeryManagersPerLowStockPart() {
        World w = newWorld();
        addParts(w, 40); // piezas 0,4,8,... quedan en stock bajo al consumir 1
        Printer printer = newPrinter(w.workspaceId, "P");
        MaintenanceOrder o = new MaintenanceOrder();
        o.setPrinter(printer);
        o.setAssignedTo(w.tech);
        o.setType(OrderType.PREVENTIVE);
        o.setStatus(OrderStatus.IN_REVIEW);
        o.setWorkspaceId(w.workspaceId);
        for (int idx : new int[]{0, 4, 8}) {
            OrderPart op = new OrderPart();
            op.setQuantity(1);
            op.setSparePart(w.parts.get(idx));
            o.addPart(op);
        }
        Long orderId = orderRepository.save(o).getId();
        loginAs(w.manager);

        var m = measure("OrderService.changeStatus -> COMPLETED (3 low-stock parts)",
                () -> orderService.changeStatus(orderId, new StatusChangeRequest("COMPLETED", null), w.manager.getEmail()));

        assertThat(m.result().status()).isEqualTo("COMPLETED");
        assertThat(m.m().statements()).isLessThanOrEqualTo(MAX_COMPLETE_STATEMENTS);
    }

    // Linea base medida: 23 sentencias (managers consultados una vez por pieza en stock bajo); objetivo: 20.
    private static final long MAX_COMPLETE_STATEMENTS = 20;
}
