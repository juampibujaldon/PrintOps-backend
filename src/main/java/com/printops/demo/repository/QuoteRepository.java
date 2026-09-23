// src/main/java/com/printops/demo/repository/QuoteRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.Quote;
import com.printops.demo.entity.QuoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    // Búsqueda combinada con filtros opcionales (US-11).
    @Query("""
            SELECT q FROM Quote q
            WHERE (:status IS NULL OR q.status = :status)
              AND (:clientName IS NULL OR LOWER(q.clientName) LIKE LOWER(CONCAT('%', :clientName, '%')))
              AND (:printerId IS NULL OR q.printer.id = :printerId)
              AND (:from IS NULL OR q.createdAt >= :from)
              AND (:to IS NULL OR q.createdAt <= :to)
            ORDER BY q.createdAt DESC
            """)
    List<Quote> search(@Param("status") QuoteStatus status,
                       @Param("clientName") String clientName,
                       @Param("printerId") Long printerId,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to);
}
