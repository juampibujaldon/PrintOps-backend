// src/main/java/com/printops/demo/repository/OrderPartRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.OrderPart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

// Agregados de piezas usadas en órdenes (US-08). Todas las métricas de costo
// se calculan sobre órdenes COMPLETED dentro del período.
public interface OrderPartRepository extends JpaRepository<OrderPart, Long> {

    @Query("SELECT COALESCE(SUM(op.quantity * p.unitPrice), 0.0) FROM OrderPart op JOIN op.sparePart p " +
            "WHERE op.order.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND op.order.createdAt >= :from AND op.order.createdAt < :to")
    Double totalPartsCostInPeriod(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT op.order.printer.id, COALESCE(SUM(op.quantity * p.unitPrice), 0.0) FROM OrderPart op JOIN op.sparePart p " +
            "WHERE op.order.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND op.order.createdAt >= :from AND op.order.createdAt < :to " +
            "GROUP BY op.order.printer.id")
    List<Object[]> partsCostByPrinter(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT YEAR(op.order.createdAt), MONTH(op.order.createdAt), COALESCE(SUM(op.quantity * p.unitPrice), 0.0) " +
            "FROM OrderPart op JOIN op.sparePart p " +
            "WHERE op.order.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND op.order.createdAt >= :from AND op.order.createdAt < :to " +
            "GROUP BY YEAR(op.order.createdAt), MONTH(op.order.createdAt)")
    List<Object[]> partsCostByMonth(@Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT op.order.printer.id, COALESCE(SUM(op.quantity * p.unitPrice), 0.0) FROM OrderPart op JOIN op.sparePart p " +
            "WHERE op.order.type = com.printops.demo.entity.OrderType.CORRECTIVE " +
            "AND op.order.status = com.printops.demo.entity.OrderStatus.COMPLETED " +
            "AND op.order.createdAt >= :from AND op.order.createdAt < :to " +
            "GROUP BY op.order.printer.id")
    List<Object[]> correctivePartsCostByPrinter(@Param("from") Instant from, @Param("to") Instant to);
}
