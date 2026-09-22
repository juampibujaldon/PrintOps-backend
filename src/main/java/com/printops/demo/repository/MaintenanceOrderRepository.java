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
    @Query("SELECT COALESCE(SUM(op.quantity * p.unitPrice), 0.0) FROM OrderPart op JOIN op.part p " +
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
    @Query("SELECT op.part.id, op.part.name, op.part.partNumber, SUM(op.quantity), SUM(op.quantity * op.part.unitPrice) " +
            "FROM OrderPart op WHERE op.order.printer.id = :printerId AND op.part IS NOT NULL " +
            "GROUP BY op.part.id, op.part.name, op.part.partNumber " +
            "ORDER BY SUM(op.quantity) DESC")
    List<Object[]> getPartUsageSummary(@Param("printerId") Long printerId);
}
