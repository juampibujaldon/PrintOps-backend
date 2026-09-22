// src/main/java/com/printops/demo/repository/MaintenanceOrderRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.MaintenanceOrder;
import com.printops.demo.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MaintenanceOrderRepository extends JpaRepository<MaintenanceOrder, Long> {
    List<MaintenanceOrder> findByPrinterId(Long printerId);
    List<MaintenanceOrder> findByStatus(OrderStatus status);
    List<MaintenanceOrder> findByPrinterIdAndStatus(Long printerId, OrderStatus status);

    // US-06: detecta si ya existe una orden abierta generada por una regla.
    boolean existsByMaintenanceRuleIdAndStatusIn(Long maintenanceRuleId, Collection<OrderStatus> statuses);
}
