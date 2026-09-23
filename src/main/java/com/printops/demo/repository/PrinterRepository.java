// src/main/java/com/printops/demo/repository/PrinterRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.Printer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PrinterRepository extends JpaRepository<Printer, Long> {

    // FIX 3: todas las consultas están filtradas por workspace.
    Optional<Printer> findByIdAndWorkspaceId(Long id, Long workspaceId);

    Optional<Printer> findBySerialNumberAndWorkspaceId(String serialNumber, Long workspaceId);

    List<Printer> findByWorkspaceId(Long workspaceId);

    List<Printer> findByWorkspaceIdAndLocationContainingIgnoreCase(Long workspaceId, String location);

    List<Printer> findByWorkspaceIdAndNextMaintenanceDateBefore(Long workspaceId, LocalDate date);
}
