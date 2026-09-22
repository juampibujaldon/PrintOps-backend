// src/main/java/com/printops/demo/repository/SparePartRepository.java
package com.printops.demo.repository;

import com.printops.demo.entity.SparePart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SparePartRepository extends JpaRepository<SparePart, Long> {

    List<SparePart> findAllByOrderByNameAsc();

    List<SparePart> findByNameContainingIgnoreCaseOrPartNumberContainingIgnoreCase(String name, String partNumber);

    List<SparePart> findByCategory(String category);

    Optional<SparePart> findByPartNumber(String partNumber);

    boolean existsByPartNumber(String partNumber);

    // Stock bajo: stock <= minStock (comparación entre columnas → JPQL).
    @Query("SELECT p FROM SparePart p WHERE p.stock <= p.minStock ORDER BY p.name ASC")
    List<SparePart> findByStockLessThanEqualMinStock();

    // Categorías distintas registradas.
    @Query("SELECT DISTINCT p.category FROM SparePart p WHERE p.category IS NOT NULL AND p.category <> '' ORDER BY p.category ASC")
    List<String> findDistinctCategories();
}
