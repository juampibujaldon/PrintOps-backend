package com.printops.demo.service;

import com.printops.demo.dto.ActivityItemDTO;
import com.printops.demo.dto.PageResponse;
import com.printops.demo.security.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Registro de actividad del taller: quién hizo qué y cuándo. Se arma con datos que ya se
// guardaban (historial de estados de órdenes, movimientos de stock, accesos y trabajos de
// impresión) en una sola consulta paginada en la base.
@Service
public class ActivityService {

    static final Set<String> KINDS = Set.of("ORDER_STATUS", "STOCK", "LOGIN", "LOGOUT", "LOGIN_FAILED", "JOB");

    private static final String UNION = """
            SELECT 'ORDER_STATUS' AS kind, sh.changed_at AS at, u.email AS actor, sh.order_id AS ref_id,
                   'Orden #' || sh.order_id AS ref,
                   COALESCE(sh.from_status || ' → ', '') || sh.to_status || COALESCE(' · ' || sh.comment, '') AS detail
            FROM status_history sh
            JOIN maintenance_orders o ON o.id = sh.order_id
            LEFT JOIN users u ON u.id = sh.changed_by
            WHERE o.workspace_id = ?
            UNION ALL
            SELECT 'STOCK', sm.performed_at, u.email, sm.spare_part_id, sp.name,
                   sm.type || ' ' || CASE WHEN sm.quantity_change > 0 THEN '+' ELSE '' END || sm.quantity_change
                       || ' (' || sm.quantity_before || ' → ' || sm.quantity_after || ')' || COALESCE(' · ' || sm.note, '')
            FROM stock_movements sm
            JOIN spare_parts sp ON sp.id = sm.spare_part_id
            LEFT JOIN users u ON u.id = sm.performed_by
            WHERE sp.workspace_id = ?
            UNION ALL
            SELECT CASE la.action WHEN 'FAILED' THEN 'LOGIN_FAILED' ELSE la.action END, la."timestamp", la.email,
                   la.user_id, NULL, COALESCE(la.ip_address, '')
            FROM login_audit la
            JOIN users u ON u.id = la.user_id
            WHERE u.workspace_id = ?
            UNION ALL
            SELECT 'JOB', COALESCE(j.finished_at, j.started_at, j.created_at), u.email, j.id, j.title, j.status
            FROM print_jobs j
            LEFT JOIN users u ON u.id = j.created_by
            WHERE j.workspace_id = ?
            """;

    private final JdbcTemplate jdbc;

    public ActivityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityItemDTO> page(String kind, Integer page, Integer size) {
        Long ws = TenantContext.getCurrentWorkspaceId();
        var req = PageResponse.request(page, size);
        String filter = kind != null && KINDS.contains(kind) ? " WHERE kind = ?" : "";
        List<Object> args = new ArrayList<>(List.of(ws, ws, ws, ws));
        if (!filter.isEmpty()) args.add(kind);

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + UNION + ") a" + filter, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(req.getPageSize());
        pageArgs.add(req.getOffset());
        List<ActivityItemDTO> items = jdbc.query(
                "SELECT * FROM (" + UNION + ") a" + filter + " ORDER BY at DESC NULLS LAST LIMIT ? OFFSET ?",
                (rs, i) -> {
                    Timestamp at = rs.getTimestamp("at");
                    long ref = rs.getLong("ref_id");
                    return new ActivityItemDTO(rs.getString("kind"), at != null ? at.toInstant() : null,
                            rs.getString("actor"), rs.wasNull() ? null : ref, rs.getString("ref"), rs.getString("detail"));
                }, pageArgs.toArray());
        long t = total != null ? total : 0;
        int pages = (int) Math.ceil(t / (double) req.getPageSize());
        return new PageResponse<>(items, req.getPageNumber(), req.getPageSize(), t, pages,
                req.getPageNumber() + 1 >= pages);
    }
}
