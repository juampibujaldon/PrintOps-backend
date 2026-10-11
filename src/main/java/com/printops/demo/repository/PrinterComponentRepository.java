// src/main/java/com/printops/demo/repository/PrinterComponentRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.ComponentType;
import com.printops.demo.entity.PrinterComponent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrinterComponentRepository extends JpaRepository<PrinterComponent, Long> {

    List<PrinterComponent> findByPrinterId(Long printerId);

    Optional<PrinterComponent> findByPrinterIdAndComponent(Long printerId, ComponentType component);
}
