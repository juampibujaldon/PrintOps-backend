package com.printops.demo.perf;

import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// El esquema (ddl-auto) debe crear los indices de los filtros y FKs calientes.
class SchemaIndexesIT extends IntegrationTestBase {

    private boolean hasIndex(String table, String columns) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?", table);
        // Cualquier indice (no unico incluido) cuyas columnas EMPIECEN por las pedidas, en ese orden.
        return rows.stream().map(r -> ((String) r.get("indexdef")).replace("\"", "")) // "timestamp" va entre comillas
                .anyMatch(def -> def.contains("(" + columns + ")") || def.contains("(" + columns + ","));
    }

    @Test
    void ordersHaveWorkspaceStatusDateAndPrinterIndexes() {
        assertThat(hasIndex("maintenance_orders", "workspace_id, status, created_at")).isTrue();
        assertThat(hasIndex("maintenance_orders", "printer_id, workspace_id, created_at")).isTrue();
        assertThat(hasIndex("maintenance_orders", "maintenance_rule_id, status")).isTrue();
    }

    @Test
    void orderChildTablesAreIndexedByOrderId() {
        assertThat(hasIndex("order_parts", "order_id")).isTrue();
        assertThat(hasIndex("order_photos", "order_id")).isTrue();
        assertThat(hasIndex("order_checklist_items", "order_id")).isTrue();
        assertThat(hasIndex("status_history", "order_id")).isTrue();
    }

    @Test
    void otherHotColumnsAreIndexed() {
        assertThat(hasIndex("stock_movements", "spare_part_id")).isTrue();
        assertThat(hasIndex("notifications", "recipient_user_id")).isTrue();
        assertThat(hasIndex("printers", "workspace_id")).isTrue();
        assertThat(hasIndex("quotes", "workspace_id")).isTrue();
        assertThat(hasIndex("maintenance_rules", "active")).isTrue();
    }

    @Test
    void authTablesAreIndexed() {
        assertThat(hasIndex("refresh_tokens", "expires_at")).isTrue();
        assertThat(hasIndex("refresh_tokens", "user_id")).isTrue();
        assertThat(hasIndex("token_blacklist", "expires_at")).isTrue();
        assertThat(hasIndex("login_audit", "ip_address, timestamp")).isTrue();
    }
}
