package com.printops.demo.repository;

import com.printops.demo.entity.PrintJob;
import com.printops.demo.entity.PrintJobStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PrintJobRepository extends JpaRepository<PrintJob, Long> {

    // Cola: activos primero (imprimiendo, luego en espera por prioridad y antigüedad).
    @EntityGraph(attributePaths = {"printer", "spool", "quote"})
    @Query("""
            SELECT j FROM PrintJob j
            WHERE j.workspaceId = :workspaceId
              AND j.status IN :statuses
              AND (:printerId IS NULL OR j.printer.id = :printerId)
            ORDER BY CASE j.status WHEN com.printops.demo.entity.PrintJobStatus.PRINTING THEN 0
                                   WHEN com.printops.demo.entity.PrintJobStatus.QUEUED THEN 1 ELSE 2 END,
                     j.priority DESC, j.createdAt ASC, j.id ASC
            """)
    List<PrintJob> findQueue(@Param("workspaceId") Long workspaceId,
                             @Param("statuses") Collection<PrintJobStatus> statuses,
                             @Param("printerId") Long printerId);

    @EntityGraph(attributePaths = {"printer", "spool", "quote"})
    Optional<PrintJob> findByIdAndWorkspaceId(Long id, Long workspaceId);

    boolean existsByQuoteId(Long quoteId);

    @EntityGraph(attributePaths = {"spool", "quote"})
    List<PrintJob> findByPrinterIdAndStatusOrderByStartedAtAsc(Long printerId, PrintJobStatus status);

    @EntityGraph(attributePaths = {"spool", "quote"})
    List<PrintJob> findByPrinterIdAndStatusOrderByPriorityDescCreatedAtAsc(Long printerId, PrintJobStatus status);

    @EntityGraph(attributePaths = "printer")
    @Query("SELECT j FROM PrintJob j WHERE j.workspaceId = :workspaceId AND j.status = :status " +
            "AND j.finishedAt >= :from AND j.finishedAt < :to ORDER BY j.finishedAt DESC")
    List<PrintJob> findFinishedInPeriod(@Param("workspaceId") Long workspaceId, @Param("status") PrintJobStatus status,
                                        @Param("from") Instant from, @Param("to") Instant to);
}
