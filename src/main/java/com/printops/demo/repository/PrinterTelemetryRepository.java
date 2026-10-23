package com.printops.demo.repository;

import com.printops.demo.entity.PrinterTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrinterTelemetryRepository extends JpaRepository<PrinterTelemetry, Long> {

    List<PrinterTelemetry> findByWorkspaceId(Long workspaceId);
}
