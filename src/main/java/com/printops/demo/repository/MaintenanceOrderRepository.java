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

public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrder, Long> {
    List<MaintenanceOrder> findByPrinterId(Long printerId);
    List<MaintenanceOrder> findByStatus(OrderStatus status);
    List<MaintenanceOrder> findByPrinterIdAndStatus(Long printerId, OrderStatus status);

    // US-06: detecta si ya existe una orden abierta generada por una regla.
    boolean existsByMaintenanceRuleIdAndStatusIn(Long maintenanceRuleId, Collection<OrderStatus> statuses);

    // ── US-07: historial de mantenimiento por impresora ──────────────────────
    List<MaintenanceOrder> findByPrinterIdOrderByCreatedAtDesc(Long printerId);

    List<MaintenanceOrder> findByPrinterIdAndTypeOrderByCreatedAtDesc(Long printerId, OrderType type);

    List<MaintenanceOrder> findByPrinterIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long printerId, Instant from, Instant to);

    List<MaintenanceOrder> findByPrinterIdAndTypeAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long printerId, OrderType type, Instant from, Instant to);

    // Cantidad de órdenes COMPLETED de una impresora.
    long countByPrinterIdAndStatus(Long printerId, OrderStatus status);

    // Conteo de órdenes agrupadas por tipo (US-07).
    @Query("SELECT o.type, COUNT(o) FROM MaintenanceOrder o WHERE o.printer.id = :printerId GROUP BY o.type")
    List<Object[]> countByTypeForPrinter(@Param("printerId") Long printerId);

    // Costo total de piezas en órdenes COMPLETED de una impresora.
    @Query("SELECT COALESCE(SUM(op.quantity * p.unitPrice), 0.0) FROM OrderPart op JOIN op.sparePart p " +
            "WHERE op.order.printer.id = :printerId AND op.order.status = com.printops.demo.entity.OrderStatus.COMPLETED")
    Double getTotalPartsCostByPrinter(@Param("printerId") Long printerId);

    // Fechas de creación de órdenes correctivas COMPLETED (para el MTBF).
    @Query("SELECT o.createdAt FROM MaintenanceOrder o " +
            "WHERE o.printer.id = :printerId " +
            "AND o.type = com.printops.demo.entity.OrderType.CORRECTIVE " +
            "AND o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "ORDER BY o.createdAt ASC")
    List<Instant> getCorrectiveOrderDates(@Param("printerId") Long printerId);

    // Uso agregado de piezas por impresora, ordenado por cantidad desc.
    @Query("SELECT op.sparePart.id, op.sparePart.name, op.sparePart.partNumber, SUM(op.quantity), SUM(op.quantity * op.sparePart.unitPrice) " +
            "FROM OrderPart op WHERE op.order.printer.id = :printerId AND op.sparePart IS NOT NULL " +
            "GROUP BY op.sparePart.id, op.sparePart.name, op.sparePart.partNumber " +
            "ORDER BY SUM(op.quantity) DESC")
    List<Object[]> getPartUsageSummary(@Param("printerId") Long printerId);

    // ── US-08: agregados para el dashboard de métricas globales ──────────────
    @Query("SELECT o.status, COUNT(o) FROM MaintenanceOrder o " +
            "WHERE o.createdAt >= :from AND o.createdAt < :to GROUP BY o.status")
    List<Object[]> countByStatusInPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT o.type, COUNT(o) FROM MaintenanceOrder o " +
            "WHERE o.createdAt >= :from AND o.createdAt < :to GROUP BY o.type")
    List<Object[]> countByTypeInPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT o.printer.id, COALESCE(SUM(o.actualTimeMinutes), 0) FROM MaintenanceOrder o " +
            "WHERE o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND o.createdAt >= :from AND o.createdAt < :to GROUP BY o.printer.id")
    List<Object[]> sumActualMinutesByPrinter(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT COALESCE(SUM(o.actualTimeMinutes), 0) FROM MaintenanceOrder o " +
            "WHERE o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND o.createdAt >= :from AND o.createdAt < :to")
    Long totalActualMinutesInPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT YEAR(o.createdAt), MONTH(o.createdAt), COALESCE(SUM(o.actualTimeMinutes), 0) " +
            "FROM MaintenanceOrder o WHERE o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND o.createdAt >= :from AND o.createdAt < :to " +
            "GROUP BY YEAR(o.createdAt), MONTH(o.createdAt)")
    List<Object[]> laborMinutesByMonth(@Param("from") Instant from, @Param("to") Instant to);

    // createdAt/updatedAt de COMPLETED para el tiempo promedio de resolución.
    @Query("SELECT o.createdAt, o.updatedAt FROM MaintenanceOrder o " +
            "WHERE o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND o.createdAt >= :from AND o.createdAt < :to")
    List<Object[]> completedTimestamps(@Param("from") Instant from, @Param("to") Instant to);

    // Órdenes abiertas (PENDING/IN_PROGRESS) creadas antes del umbral → vencidas.
    @Query("SELECT COUNT(o) FROM MaintenanceOrder o " +
            "WHERE o.status IN (com.printops.demo.entity.OrderStatus.PENDING, com.printops.demo.entity.OrderStatus.IN_PROGRESS) " +
            "AND o.createdAt < :threshold")
    long countOverdue(@Param("threshold") Instant threshold);

    // Top impresoras por cantidad de correctivas COMPLETED en el período.
    @Query("SELECT o.printer.id, o.printer.name, COUNT(o) FROM MaintenanceOrder o " +
            "WHERE o.type = com.printops.demo.entity.OrderType.CORRECTIVE " +
            "AND o.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND o.createdAt >= :from AND o.createdAt < :to " +
            "GROUP BY o.printer.id, o.printer.name ORDER BY COUNT(o) DESC")
    List<Object[]> topFailingPrinters(@Param("from") Instant from, @Param("to") Instant to);
}
