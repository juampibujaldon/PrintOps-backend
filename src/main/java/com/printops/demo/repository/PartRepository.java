// src/main/java/com/printops/demo/repository/PartRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.Part;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, Long> {
    Optional<Part> findByPartNumber(String partNumber);
    boolean existsByPartNumber(String partNumber);
    List<Part> findByNameContainingIgnoreCase(String name);

    // US-08: piezas con stock por debajo de su mínimo (stock <= minStock).
    @Query("SELECT p FROM Part p WHERE p.stockQuantity <= p.minStock ORDER BY (p.minStock - p.stockQuantity) DESC")
    List<Part> findLowStock();
}
