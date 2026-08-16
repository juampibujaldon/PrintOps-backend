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
    Optional<Printer> findBySerialNumber(String serialNumber);

    // FIX 3: filtro por ubicación (búsqueda parcial, case-insensitive).
    List<Printer> findByLocationContainingIgnoreCase(String location);

    // FIX 4: detecta impresoras con mantenimiento vencido.
    List<Printer> findByNextMaintenanceDateBefore(LocalDate date);
}
