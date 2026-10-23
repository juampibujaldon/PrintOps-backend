// src/main/java/com/printops/demo/repository/MaintenanceOrderRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.MaintenanceOrder;
import com.printops.demo.entity.OrderStatus;
import com.printops.demo.entity.OrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrder, Long> {

    // ── FIX 3: consultas filtradas por workspace ──────────────────────────────
    // El detalle de orden siempre expone el tecnico asignado: se trae en la misma consulta.
    @EntityGraph(attributePaths = "assignedTo")
    Optional<MaintenanceOrder> findByIdAndWorkspaceId(Long id, Long workspaceId);

    List<MaintenanceOrder> findByWorkspaceId(Long workspaceId);

    // Listado de ordenes: filtros opcionales y orden resuelto en SQL (IN_REVIEW primero,
    // luego mas recientes). Solo se hace fetch del tecnico (to-one); las colecciones se
    // cargan por lotes (@BatchSize) para no multiplicar filas ni caer en MultipleBagFetchException.
    @Query("""
            SELECT o FROM MaintenanceOrder o LEFT JOIN FETCH o.assignedTo
            WHERE o.workspaceId = :workspaceId
              AND (:printerId IS NULL OR o.printer.id = :printerId)
              AND (:status IS NULL OR o.status = :status)
            ORDER BY CASE WHEN o.status = com.printops.demo.entity.OrderStatus.IN_REVIEW THEN 0 ELSE 1 END,
                     o.createdAt DESC, o.id DESC
            """)
    List<MaintenanceOrder> findForList(@Param("workspaceId") Long workspaceId,
                                       @Param("printerId") Long printerId,
                                       @Param("status") OrderStatus status);

    // Misma consulta, paginada en SQL. El fetch es solo to-one, asi que LIMIT/OFFSET se
    // aplica en la base (con colecciones Hibernate paginaria en memoria).
    @Query(value = """
            SELECT o FROM MaintenanceOrder o LEFT JOIN FETCH o.assignedTo
            WHERE o.workspaceId = :workspaceId
              AND (:printerId IS NULL OR o.printer.id = :printerId)
              AND (:status IS NULL OR o.status = :status)
            ORDER BY CASE WHEN o.status = com.printops.demo.entity.OrderStatus.IN_REVIEW THEN 0 ELSE 1 END,
                     o.createdAt DESC, o.id DESC
            """,
            countQuery = """
            SELECT COUNT(o) FROM MaintenanceOrder o
            WHERE o.workspaceId = :workspaceId
              AND (:printerId IS NULL OR o.printer.id = :printerId)
              AND (:status IS NULL OR o.status = :status)
            """)
    org.springframework.data.domain.Page<MaintenanceOrder> findPageForList(@Param("workspaceId") Long workspaceId,
                                                                          @Param("printerId") Long printerId,
                                                                          @Param("status") OrderStatus status,
                                                                          org.springframework.data.domain.Pageable pageable);

    // US-06: cross-tenant (lo usa el scheduler). No se filtra por workspace.
    boolean existsByMaintenanceRuleIdAndStatusIn(Long maintenanceRuleId, Collection<OrderStatus> statuses);

    // ── US-07: historial de mantenimiento por impresora (workspace) ──────────
    // Paginado en la base. from/to nunca son null (Hibernate 7 bindea un null como bytea):
    // el servicio pasa limites abiertos cuando no hay filtro de fechas.
    @EntityGraph(attributePaths = "assignedTo")
    @Query("""
            SELECT o FROM MaintenanceOrder o
            WHERE o.printer.id = :printerId AND o.workspaceId = :workspaceId
              AND (:type IS NULL OR o.type = :type)
              AND o.createdAt >= :from AND o.createdAt <= :to
            ORDER BY o.createdAt DESC, o.id DESC
            """)
    Page<MaintenanceOrder> findHistory(@Param("printerId") Long printerId,
                                       @Param("workspaceId") Long workspaceId,
                                       @Param("type") OrderType type,
                                       @Param("from") Instant from,
                                       @Param("to") Instant to,
                                       Pageable pageable);

    long countByPrinterIdAndWorkspaceIdAndStatus(Long printerId, Long workspaceId, OrderStatus status);

    @Query("SELECT o.type, COUNT(o) FROM MaintenanceOrder o " +
            "WHERE o.printer.id = :printerId AND o.workspaceId = :workspaceId GROUP BY o.type")
    List<Object[]> countByTypeForPrinter(@Param("printerId") Long printerId, @Param("workspaceId") Long workspaceId);

    @Query("SELECT COALESCE(SUM(op.quantity * p.unitPrice), 0.0) FROM OrderPart op JOIN op.sparePart p " +
            "WHERE op.order.printer.id = :printerId AND op.order.workspaceId = :workspaceId " +
            "AND op.order.status = com.printops.demo.entity.OrderStatus.COMPLETED")
    Double getTotalPartsCostByPrinter(@Param("printerId") Long printerId, @Param("workspaceId") Long workspaceId);

    // MTBF en SQL: promedio de dias enteros (truncados) entre correctivas COMPLETED consecutivas.
    // null si hay menos de dos (LAG da null en la primera fila y AVG ignora nulls).
    @Query(value = """
            SELECT CAST(AVG(gap) AS double precision) FROM (
                SELECT TRUNC(EXTRACT(EPOCH FROM (created_at - LAG(created_at) OVER (ORDER BY created_at))) / 86400) AS gap
                FROM maintenance_orders
                WHERE printer_id = :printerId AND workspace_id = :workspaceId
                  AND type = 'CORRECTIVE' AND status = 'COMPLETED'
            ) g
            """, nativeQuery = true)
    Double averageDaysBetweenCorrectives(@Param("printerId") Long printerId, @Param("workspaceId") Long workspaceId);

    @Query("SELECT op.sparePart.id, op.sparePart.name, op.sparePart.partNumber, SUM(op.quantity), SUM(op.quantity * op.sparePart.unitPrice) " +
            "FROM OrderPart op WHERE op.order.printer.id = :printerId AND op.order.workspaceId = :workspaceId AND op.sparePart IS NOT NULL " +
            "GROUP BY op.sparePart.id, op.sparePart.name, op.sparePart.partNumber ORDER BY SUM(op.quantity) DESC")
    List<Object[]> getPartUsageSummary(@Param("printerId") Long printerId, @Param("workspaceId") Long workspaceId);

    // ── US-08: agregados del dashboard (workspace) ───────────────────────────
    @Query("SELECT o.status, COUNT(o) FROM MaintenanceOrder o " +
            "WHERE o.workspaceId = :workspaceId AND o.createdAt >= :from AND o.createdAt < :to GROUP BY o.status")
    List<Object[]> countByStatusInPeriod(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT o.type, COUNT(o) FROM MaintenanceOrder o " +
            "WHERE o.workspaceId = :workspaceId AND o.createdAt >= :from AND o.createdAt < :to GROUP BY o.type")
    List<Object[]> countByTypeInPeriod(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT o.printer.id, COALESCE(SUM(o.actualTimeMinutes), 0) FROM MaintenanceOrder o " +
            "WHERE o.workspaceId = :workspaceId AND o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND o.createdAt >= :from AND o.createdAt < :to GROUP BY o.printer.id")
    List<Object[]> sumActualMinutesByPrinter(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COALESCE(SUM(o.actualTimeMinutes), 0) FROM MaintenanceOrder o " +
            "WHERE o.workspaceId = :workspaceId AND o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND o.createdAt >= :from AND o.createdAt < :to")
    Long totalActualMinutesInPeriod(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT YEAR(o.createdAt), MONTH(o.createdAt), COALESCE(SUM(o.actualTimeMinutes), 0) " +
            "FROM MaintenanceOrder o WHERE o.workspaceId = :workspaceId AND o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND o.createdAt >= :from AND o.createdAt < :to GROUP BY YEAR(o.createdAt), MONTH(o.createdAt)")
    List<Object[]> laborMinutesByMonth(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    // Promedio de resolucion (horas) en SQL: minutos enteros (truncados) por orden, promediados.
    // null si no hay ordenes completadas en el periodo.
    @Query(value = """
            SELECT CAST(AVG(TRUNC(EXTRACT(EPOCH FROM (updated_at - created_at)) / 60)) / 60.0 AS double precision)
            FROM maintenance_orders
            WHERE workspace_id = :workspaceId AND status = 'COMPLETED'
              AND created_at >= :from AND created_at < :to
            """, nativeQuery = true)
    Double averageResolutionHours(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COUNT(o) FROM MaintenanceOrder o " +
            "WHERE o.workspaceId = :workspaceId " +
            "AND o.status IN (com.printops.demo.entity.OrderStatus.PENDING, com.printops.demo.entity.OrderStatus.IN_PROGRESS) " +
            "AND o.createdAt < :threshold")
    long countOverdue(@Param("workspaceId") Long workspaceId, @Param("threshold") Instant threshold);

    @Query("SELECT o.printer.id, o.printer.name, COUNT(o) FROM MaintenanceOrder o " +
            "WHERE o.workspaceId = :workspaceId AND o.type = com.printops.demo.entity.OrderType.CORRECTIVE " +
            "AND o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND o.createdAt >= :from AND o.createdAt < :to " +
            "GROUP BY o.printer.id, o.printer.name ORDER BY COUNT(o) DESC")
    List<Object[]> topFailingPrinters(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);
}
