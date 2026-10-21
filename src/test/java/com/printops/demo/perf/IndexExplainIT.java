package com.printops.demo.perf;

import com.printops.demo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

// Medicion manual (no corre en el build normal): plan y tiempo de las consultas calientes
// con y sin los indices nuevos sobre 300.000 ordenes. Activar con PERF_EXPLAIN=true.
@EnabledIfEnvironmentVariable(named = "PERF_EXPLAIN", matches = "true")
class IndexExplainIT extends IntegrationTestBase {

    private final StringBuilder out = new StringBuilder();

    private void log(String s) {
        out.append(s).append(System.lineSeparator());
        System.out.println(s);
    }

    // Ejecuta EXPLAIN ANALYZE 4 veces (calentamiento) y devuelve el plan + tiempo de la ultima.
    private String explain(String label, String sql, Object... args) {
        List<String> plan = null;
        for (int i = 0; i < 4; i++) {
            plan = jdbc.queryForList("EXPLAIN (ANALYZE, BUFFERS OFF) " + sql, String.class, args);
        }
        String time = plan.stream().filter(l -> l.startsWith("Execution Time")).findFirst().orElse("?");
        String node = plan.get(0).trim();
        log(String.format("  %-34s %-28s | %s", label, time, node.length() > 90 ? node.substring(0, 90) : node));
        return time;
    }

    private void runQueries(String phase) {
        log(phase);
        explain("list ws+status, newest first",
                "SELECT * FROM maintenance_orders WHERE workspace_id = ? AND status = 'IN_PROGRESS' ORDER BY created_at DESC LIMIT 50", 7);
        explain("history page (printer, ws)",
                "SELECT * FROM maintenance_orders WHERE printer_id = ? AND workspace_id = ? ORDER BY created_at DESC LIMIT 20", 107, 7);
        explain("history count (printer, ws)",
                "SELECT count(*) FROM maintenance_orders WHERE printer_id = ? AND workspace_id = ?", 107, 7);
        explain("rule has open order",
                "SELECT 1 FROM maintenance_orders WHERE maintenance_rule_id = ? AND status IN ('PENDING','IN_PROGRESS','IN_REVIEW') LIMIT 1", 123456);
        explain("order_parts of 50 orders (batch)",
                "SELECT * FROM order_parts WHERE order_id = ANY (SELECT id FROM maintenance_orders WHERE workspace_id = 7 ORDER BY id LIMIT 50)");
        explain("notifications of one user",
                "SELECT * FROM notifications WHERE recipient_user_id = ? ORDER BY created_at DESC", 42);
    }

    @Test
    void explainWithAndWithoutIndexes() throws IOException {
        // 500 impresoras en 50 workspaces; 300.000 ordenes; 2 piezas externas por orden; 200.000 notificaciones.
        jdbc.execute("INSERT INTO printers (id, brand, model, serial_number, purchase_date, status, workspace_id, qr_code_data, total_printing_hours, total_filament_grams) "
                + "SELECT 100 + g, 'B', 'M', 'PS-' || g, DATE '2024-01-01', 'OPERATIVE', 1 + (g % 50), 'qr-' || g, 0, 0 FROM generate_series(1, 500) g");
        jdbc.execute("INSERT INTO maintenance_orders (printer_id, type, status, workspace_id, created_at, updated_at, version, maintenance_rule_id) "
                + "SELECT 100 + 1 + (g % 500), (ARRAY['PREVENTIVE','CORRECTIVE','CALIBRATION'])[1 + g % 3], "
                + "(ARRAY['PENDING','IN_PROGRESS','IN_REVIEW','COMPLETED','COMPLETED'])[1 + g % 5], 1 + ((g % 500) % 50), "
                + "now() - (g || ' minutes')::interval, now(), 0, g FROM generate_series(1, 300000) g");
        jdbc.execute("INSERT INTO order_parts (order_id, quantity, external_part_name) "
                + "SELECT o.id, 1 + k, 'ext' FROM maintenance_orders o CROSS JOIN generate_series(0, 1) k");
        jdbc.execute("INSERT INTO notifications (message, recipient_user_id, type, read, created_at) "
                + "SELECT 'm' || g, g % 500, 'T', false, now() - (g || ' seconds')::interval FROM generate_series(1, 200000) g");
        jdbc.execute("ANALYZE");

        log("== EXPLAIN ANALYZE sobre 300.000 ordenes / 600.000 piezas / 200.000 notificaciones (Postgres 16, contenedor) ==");
        runQueries("CON indices nuevos:");

        for (String idx : List.of("idx_orders_ws_status_created", "idx_orders_printer_ws_created", "idx_orders_rule_status",
                "idx_order_parts_order", "idx_notifications_recipient")) {
            jdbc.execute("DROP INDEX " + idx);
        }
        jdbc.execute("ANALYZE");
        runQueries("SIN indices nuevos (solo PKs / uniques):");

        Path file = Path.of("build", "perf-explain.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, out.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
