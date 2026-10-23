package com.printops.demo.repository;

import com.printops.demo.entity.PrinterIntegration;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PrinterIntegrationRepository extends JpaRepository<PrinterIntegration, Long> {

    Optional<PrinterIntegration> findByPrinterId(Long printerId);

    @EntityGraph(attributePaths = "printer")
    Optional<PrinterIntegration> findByTokenHash(String tokenHash);

    boolean existsByPrinterId(Long printerId);
}
