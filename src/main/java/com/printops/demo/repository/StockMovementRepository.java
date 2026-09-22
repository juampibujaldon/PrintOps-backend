// src/main/java/com/printops/demo/repository/StockMovementRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    // Movimientos de una pieza, más reciente primero.
    List<StockMovement> findBySparePartIdOrderByPerformedAtDesc(Long sparePartId);

    boolean existsBySparePartId(Long sparePartId);
}
