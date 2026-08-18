// src/main/java/com/printops/demo/repository/StatusHistoryRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Historial de solo lectura: no se expone update/delete.
public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findByOrderIdOrderByChangedAtDesc(Long orderId);
}
