package com.printops.demo.service;

import com.printops.demo.security.TenantContext;
import com.printops.demo.util.Csv;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

// Exportación a CSV para contabilidad. Todo filtrado por el taller del usuario.
@Service
public class ExportService {

    public static final Set<String> DATASETS = Set.of("orders", "quotes", "parts", "movements", "jobs");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final JdbcTemplate jdbc;

    public ExportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public byte[] export(String dataset, LocalDate from, LocalDate to) {
        Long ws = TenantContext.getCurrentWorkspaceId();
        Timestamp f = Timestamp.from((from != null ? from : LocalDate.of(1970, 1, 1)).atStartOfDay(ZONE).toInstant());
        Timestamp t = Timestamp.from((to != null ? to.plusDays(1) : LocalDate.of(9999, 1, 1)).atStartOfDay(ZONE).toInstant());
        Csv csv = new Csv();
        switch (dataset) {
            case "orders" -> {
                csv.row("Orden", "Fecha", "Impresora", "Serie", "Tipo", "Estado", "Técnico", "Descripción",
                        "Minutos estimados", "Minutos reales", "Costo repuestos", "Actualizada");
                jdbc.query("""
                        SELECT o.id, o.created_at, COALESCE(p.name, p.model) AS printer, p.serial_number, o.type, o.status,
                               u.email, o.description, o.estimated_time_minutes, o.actual_time_minutes,
                               (SELECT SUM(op.quantity * COALESCE(sp.unit_price, op.external_unit_price, 0))
                                  FROM order_parts op LEFT JOIN spare_parts sp ON sp.id = op.spare_part_id
                                 WHERE op.order_id = o.id) AS parts_cost,
                               o.updated_at
                        FROM maintenance_orders o JOIN printers p ON p.id = o.printer_id
                        LEFT JOIN users u ON u.id = o.assigned_to
                        WHERE o.workspace_id = ? AND o.created_at >= ? AND o.created_at < ?
                        ORDER BY o.created_at DESC
                        """, rs -> {
                    csv.row(rs.getLong(1), ts(rs, 2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                            rs.getString(7), rs.getString(8), intOrNull(rs, 9), intOrNull(rs, 10), dbl(rs, 11), ts(rs, 12));
                }, ws, f, t);
            }
            case "quotes" -> {
                csv.row("Número", "Fecha", "Cliente", "Trabajo", "Estado", "Impresora", "Material", "Unidades",
                        "Gramos", "Horas", "Costo total", "Gastos extra", "Precio total", "Ganancia", "ROI %");
                jdbc.query("""
                        SELECT q.quote_number, q.created_at, q.client_name, q.job_description, q.status,
                               COALESCE(p.name, p.model), q.filament_type, q.units, q.filament_grams, q.printing_hours,
                               q.total_cost, q.extra_costs_total, q.total_price, q.total_profit, q.roi
                        FROM quotes q LEFT JOIN printers p ON p.id = q.printer_id
                        WHERE q.workspace_id = ? AND q.created_at >= ? AND q.created_at < ?
                        ORDER BY q.created_at DESC
                        """, rs -> {
                    csv.row(rs.getString(1), ts(rs, 2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                            rs.getString(7), intOrNull(rs, 8), dbl(rs, 9), dbl(rs, 10), dbl(rs, 11), dbl(rs, 12),
                            dbl(rs, 13), dbl(rs, 14), dbl(rs, 15));
                }, ws, f, t);
            }
            case "parts" -> {
                csv.row("Repuesto", "Número de parte", "Categoría", "Marca", "Stock", "Stock mínimo", "Precio unitario",
                        "Valor en stock", "Proveedor");
                jdbc.query("""
                        SELECT sp.name, sp.part_number, sp.category, sp.brand, sp.stock, sp.min_stock, sp.unit_price,
                               sp.stock * COALESCE(sp.unit_price, 0), s.name
                        FROM spare_parts sp LEFT JOIN suppliers s ON s.id = sp.supplier_id
                        WHERE sp.workspace_id = ? ORDER BY sp.name
                        """, rs -> {
                    csv.row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), intOrNull(rs, 5),
                            intOrNull(rs, 6), dbl(rs, 7), dbl(rs, 8), rs.getString(9));
                }, ws);
            }
            case "movements" -> {
                csv.row("Fecha", "Repuesto", "Número de parte", "Tipo", "Cambio", "Antes", "Después", "Orden", "Usuario", "Nota");
                jdbc.query("""
                        SELECT sm.performed_at, sp.name, sp.part_number, sm.type, sm.quantity_change, sm.quantity_before,
                               sm.quantity_after, sm.order_id, u.email, sm.note
                        FROM stock_movements sm JOIN spare_parts sp ON sp.id = sm.spare_part_id
                        LEFT JOIN users u ON u.id = sm.performed_by
                        WHERE sp.workspace_id = ? AND sm.performed_at >= ? AND sm.performed_at < ?
                        ORDER BY sm.performed_at DESC
                        """, rs -> {
                    csv.row(ts(rs, 1), rs.getString(2), rs.getString(3), rs.getString(4), intOrNull(rs, 5), intOrNull(rs, 6),
                            intOrNull(rs, 7), longOrNull(rs, 8), rs.getString(9), rs.getString(10));
                }, ws, f, t);
            }
            case "jobs" -> {
                csv.row("Trabajo", "Título", "Estado", "Impresora", "Presupuesto", "Unidades", "Horas estimadas",
                        "Horas reales", "Gramos estimados", "Gramos reales", "Creado", "Inicio", "Fin");
                jdbc.query("""
                        SELECT j.id, j.title, j.status, COALESCE(p.name, p.model), q.quote_number, j.units, j.estimated_hours,
                               j.actual_hours, j.filament_grams, j.actual_grams, j.created_at, j.started_at, j.finished_at
                        FROM print_jobs j LEFT JOIN printers p ON p.id = j.printer_id LEFT JOIN quotes q ON q.id = j.quote_id
                        WHERE j.workspace_id = ? AND j.created_at >= ? AND j.created_at < ?
                        ORDER BY j.created_at DESC
                        """, rs -> {
                    csv.row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), intOrNull(rs, 6),
                            dbl(rs, 7), dbl(rs, 8), dbl(rs, 9), dbl(rs, 10), ts(rs, 11), ts(rs, 12), ts(rs, 13));
                }, ws, f, t);
            }
            default -> throw new IllegalArgumentException("Exportación desconocida: " + dataset);
        }
        return csv.bytes();
    }

    private static Instant ts(ResultSet rs, int i) throws SQLException {
        Timestamp v = rs.getTimestamp(i);
        return v != null ? v.toInstant() : null;
    }

    private static Double dbl(ResultSet rs, int i) throws SQLException {
        double v = rs.getDouble(i);
        return rs.wasNull() ? null : v;
    }

    private static Integer intOrNull(ResultSet rs, int i) throws SQLException {
        int v = rs.getInt(i);
        return rs.wasNull() ? null : v;
    }

    private static Long longOrNull(ResultSet rs, int i) throws SQLException {
        long v = rs.getLong(i);
        return rs.wasNull() ? null : v;
    }
}
