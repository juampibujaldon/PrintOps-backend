// src/main/java/com/printops/demo/repository/QuoteRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.Quote;
import com.printops.demo.entity.QuoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long> {

    // Búsqueda combinada con filtros opcionales (US-11).
    // from/to nunca son null: Hibernate 7 bindea un LocalDateTime null como bytea
    // y Postgres no puede castearlo a timestamp. El servicio pasa límites abiertos.
    @Query("""
            SELECT q FROM Quote q
            WHERE q.workspaceId = :workspaceId
              AND (:status IS NULL OR q.status = :status)
              AND (CAST(:clientName AS string) IS NULL OR LOWER(q.clientName) LIKE LOWER(CONCAT('%', CAST(:clientName AS string), '%')))
              AND (:printerId IS NULL OR q.printer.id = :printerId)
              AND q.createdAt >= :from
              AND q.createdAt <= :to
            ORDER BY q.createdAt DESC
            """)
    List<Quote> search(@Param("workspaceId") Long workspaceId,
                       @Param("status") QuoteStatus status,
                       @Param("clientName") String clientName,
                       @Param("printerId") Long printerId,
                       @Param("from") LocalDateTime from,
                       @Param("to") LocalDateTime to);

    Optional<Quote> findByIdAndWorkspaceId(Long id, Long workspaceId);
}
