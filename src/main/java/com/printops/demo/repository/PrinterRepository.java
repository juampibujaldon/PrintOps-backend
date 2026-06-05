// src/main/java/com/printops/demo/repository/PrinterRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.Printer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrinterRepository extends JpaRepository<Printer, Long> {
    Optional<Printer> findBySerialNumber(String serialNumber);
}
