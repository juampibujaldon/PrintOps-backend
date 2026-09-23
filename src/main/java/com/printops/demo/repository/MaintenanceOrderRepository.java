// src/main/java/com/printops/demo/repository/MaintenanceOrderRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.MaintenanceOrder;
import com.printops.demo.entity.OrderStatus;
import com.printops.demo.entity.OrderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrder, Long> {

    // ── FIX 3: consultas filtradas por workspace ──────────────────────────────
    Optional<MaintenanceOrder> findByIdAndWorkspaceId(Long id, Long workspaceId);

    List<MaintenanceOrder> findByWorkspaceId(Long workspaceId);

    List<MaintenanceOrder> findByWorkspaceIdAndStatus(Long workspaceId, OrderStatus status);

    List<MaintenanceOrder> findByPrinterIdAndWorkspaceId(Long printerId, Long workspaceId);

    List<MaintenanceOrder> findByPrinterIdAndWorkspaceIdAndStatus(Long printerId, Long workspaceId, OrderStatus status);

    // US-06: cross-tenant (lo usa el scheduler). No se filtra por workspace.
    boolean existsByMaintenanceRuleIdAndStatusIn(Long maintenanceRuleId, Collection<OrderStatus> statuses);

    // ── US-07: historial de mantenimiento por impresora (workspace) ──────────
    List<MaintenanceOrder> findByPrinterIdAndWorkspaceIdOrderByCreatedAtDesc(Long printerId, Long workspaceId);

    List<MaintenanceOrder> findByPrinterIdAndWorkspaceIdAndTypeOrderByCreatedAtDesc(Long printerId, Long workspaceId, OrderType type);

    List<MaintenanceOrder> findByPrinterIdAndWorkspaceIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long printerId, Long workspaceId, Instant from, Instant to);

    List<MaintenanceOrder> findByPrinterIdAndWorkspaceIdAndTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long printerId, Long workspaceId, OrderType type, Instant from, Instant to);

    long countByPrinterIdAndWorkspaceIdAndStatus(Long printerId, Long workspaceId, OrderStatus status);

    @Query("SELECT o.type, COUNT(o) FROM MaintenanceOrder o " +
            "WHERE o.printer.id = :printerId AND o.workspaceId = :workspaceId GROUP BY o.type")
    List<Object[]> countByTypeForPrinter(@Param("printerId") Long printerId, @Param("workspaceId") Long workspaceId);

    @Query("SELECT COALESCE(SUM(op.quantity * p.unitPrice), 0.0) FROM OrderPart op JOIN op.sparePart p " +
            "WHERE op.order.printer.id = :printerId AND op.order.workspaceId = :workspaceId " +
            "AND op.order.status = com.printops.demo.entity.OrderStatus.COMPLETED")
    Double getTotalPartsCostByPrinter(@Param("printerId") Long printerId, @Param("workspaceId") Long workspaceId);

    @Query("SELECT o.createdAt FROM MaintenanceOrder o " +
            "WHERE o.printer.id = :printerId AND o.workspaceId = :workspaceId " +
            "AND o.type = com.printops.demo.entity.OrderType.CORRECTIVE " +
            "AND o.status = com.printops.demo.entity.OrderStatus.COMPLETED ORDER BY o.createdAt ASC")
    List<Instant> getCorrectiveOrderDates(@Param("printerId") Long printerId, @Param("workspaceId") Long workspaceId);

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

    @Query("SELECT o.createdAt, o.updatedAt FROM MaintenanceOrder o " +
            "WHERE o.workspaceId = :workspaceId AND o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND o.createdAt >= :from AND o.createdAt < :to")
    List<Object[]> completedTimestamps(@Param("workspaceId") Long workspaceId, @Param("from") Instant from, @Param("to") Instant to);

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
